import { readFileSync, writeFileSync, mkdirSync, existsSync, readdirSync, copyFileSync } from 'node:fs';
import { resolve, join, dirname, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';

export const PROJECT = 'samimine-dev';
export const PACKAGE = 'dev.studios.samimine';
const GROUP = 'samimine-piloto';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const directory = join(root, '.local/distribution');
const manifestPath = join(directory, 'prepared.json');
const ledgerPath = join(directory, 'published.json');
const firebase = join(root, 'node_modules/firebase-tools/lib/bin/firebase.js');
const json = path => JSON.parse(readFileSync(path, 'utf8').replace(/^\uFEFF/, ''));
const hash = path => createHash('sha256').update(readFileSync(path)).digest('hex');

export function appIdentity(config) {
  if (config.project_info?.project_id !== PROJECT) throw Error(`El proyecto debe ser ${PROJECT}.`);
  const clients = config.client?.filter(c => c.client_info?.android_client_info?.package_name === PACKAGE) ?? [];
  if (clients.length !== 1 || !clients[0].client_info.mobilesdk_app_id?.includes(':android:')) {
    throw Error(`Debe existir un único cliente Android para ${PACKAGE}.`);
  }
  return clients[0].client_info.mobilesdk_app_id;
}

export function parseArgs(args) {
  const result = {};
  for (let i = 0; i < args.length; i += 2) {
    if (!['--version-code', '--version-name', '--notes-file'].includes(args[i]) ||
        !args[i + 1] || args[i + 1].startsWith('--') || result[args[i]] !== undefined) {
      throw Error('Argumentos inválidos. Use --version-code N --version-name X para prepare o --notes-file archivo.txt para publish.');
    }
    result[args[i]] = args[i + 1];
  }
  return result;
}

export function validateVersion(code, name) {
  if (!/^[1-9]\d*$/.test(String(code)) || Number(code) > 2100000000 ||
      typeof name !== 'string' || !name.trim() || /[\r\n\x00]/.test(name)) {
    throw Error('Versión inválida: indique un código entero positivo y un nombre no vacío.');
  }
}

function run(command, args, capture = false, windowsBatch = false) {
  // cmd.exe only receives fixed tool paths and validated Gradle arguments. Never release notes or passwords.
  if (windowsBatch && process.platform === 'win32') {
    args = ['/d', '/s', '/c', [command, ...args].join(' ')];
    command = 'cmd.exe';
  }
  const result = spawnSync(command, args, { cwd: root, encoding: 'utf8',
    shell: false, stdio: capture ? 'pipe' : 'inherit',
    env: { ...process.env, NO_UPDATE_NOTIFIER: '1', CI: 'true' } });
  if (result.error || result.status !== 0) {
    if (capture && result.stdout) console.error(result.stdout);
    if (capture && result.stderr) console.error(result.stderr);
    throw Error(`Falló ${command.split(/[\\/]/).pop()} (código ${result.status ?? 'sin proceso'}).`);
  }
  return result.stdout ?? '';
}

function cli(args) {
  return JSON.parse(run(process.execPath, [firebase, ...args, '--project', PROJECT, '--json', '--non-interactive'], true));
}

function buildTools() {
  const properties = readFileSync(join(root, 'local.properties'), 'utf8');
  const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT ||
    properties.match(/^sdk\.dir=(.+)$/m)?.[1].trim().replace(/\\([\\: ])/g, '$1');
  if (!sdk) throw Error('Configure sdk.dir en local.properties o ANDROID_HOME.');
  const toolsDir = join(sdk, 'build-tools');
  const versions = readdirSync(toolsDir).filter(v => /^\d+\.\d+\.\d+$/.test(v))
    .sort((a, b) => a.localeCompare(b, undefined, { numeric: true })).reverse();
  const tools = versions.map(v => join(toolsDir, v)).find(p => existsSync(join(p, 'lib/apksigner.jar')) &&
    existsSync(join(p, process.platform === 'win32' ? 'aapt.exe' : 'aapt')));
  if (!tools) throw Error('Instale Android SDK Build Tools con aapt y apksigner.');
  return tools;
}

function inspectApk(apk) {
  const tools = buildTools();
  const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
  const signature = run(java, ['-jar', join(tools, 'lib/apksigner.jar'), 'verify', '--verbose', '--print-certs', apk], true);
  const certificate = signature.match(/Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]+)/)?.[1].toLowerCase();
  if (!certificate || /Signer #2 certificate/.test(signature)) throw Error('Se requiere un único firmante válido.');
  const badging = run(join(tools, process.platform === 'win32' ? 'aapt.exe' : 'aapt'), ['dump', 'badging', apk], true);
  const info = badging.match(/package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'/);
  if (!info || info[1] !== PACKAGE) throw Error('El APK no pertenece a SamiMine.');
  if (/application-debuggable/.test(badging)) throw Error('No se puede distribuir un APK debuggable.');
  return { packageName: info[1], versionCode: Number(info[2]), versionName: info[3], certificateSha256: certificate };
}

export function validateManifest(manifest, identity, actual, digest) {
  if (manifest.project !== PROJECT || manifest.appId !== identity || manifest.group !== GROUP ||
      manifest.sha256 !== digest || ['packageName', 'versionCode', 'versionName', 'certificateSha256']
        .some(key => manifest[key] !== actual[key])) throw Error('El APK o su configuración no coinciden con la preparación. Ejecute prepare otra vez.');
  validateVersion(manifest.versionCode, manifest.versionName);
}

function previous() { return existsSync(ledgerPath) ? json(ledgerPath) : []; }

export function validateHistory(info, history) {
  if (history.some(r => r.versionCode >= info.versionCode)) throw Error('versionCode debe superar todas las entregas registradas en este equipo.');
  if (history.some(r => r.certificateSha256 !== info.certificateSha256)) throw Error('La firma difiere de las entregas anteriores. Restaure la clave original.');
}

function checkHistory(info) { validateHistory(info, previous()); }

export function main(args = process.argv.slice(2)) {
  const [action, ...rest] = args;
  const options = parseArgs(rest);
  const appId = appIdentity(json(join(root, 'app/google-services.json')));
  mkdirSync(directory, { recursive: true });
  if (action === 'prepare') {
    if (options['--notes-file']) throw Error('prepare no acepta notas.');
    const versionCode = options['--version-code'];
    const versionName = options['--version-name'];
    validateVersion(versionCode, versionName);
    // The Gradle batch file uses cmd.exe on Windows. Restrict the only variable arguments.
    if (!/^[A-Za-z0-9][A-Za-z0-9._+-]*$/.test(versionName)) throw Error('versionName solo admite letras, números, punto, guion, + y _.');
    run(process.platform === 'win32' ? '.\\gradlew.bat' : './gradlew', [
      ':app:validateReleaseCredentials', ':app:testDebugUnitTest', ':app:lintRelease', ':app:assembleRelease',
      `-PversionCode=${versionCode}`, `-PversionName=${versionName}`], false, true);
    const source = join(root, 'app/build/outputs/apk/release/app-release.apk');
    const info = inspectApk(source);
    if (info.versionCode !== Number(versionCode) || info.versionName !== versionName) throw Error('Versión compilada inesperada.');
    checkHistory(info);
    const apk = join(directory, `samimine-${versionCode}-${hash(source).slice(0, 12)}.apk`);
    copyFileSync(source, apk);
    const manifest = { project: PROJECT, appId, group: GROUP, ...info, sha256: hash(apk),
      apk: relative(directory, apk), preparedAt: new Date().toISOString() };
    writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + '\n');
    console.log(`APK preparado: ${apk}\nSHA-256: ${manifest.sha256}\nNo se ha publicado ninguna versión.`);
  } else if (action === 'publish') {
    if (options['--version-code'] || options['--version-name']) throw Error('publish no acepta versiones: usa el APK preparado.');
    const notesPath = options['--notes-file'] && resolve(options['--notes-file']);
    if (!notesPath || !readFileSync(notesPath, 'utf8').trim()) throw Error('Se requiere --notes-file con notas no vacías.');
    const manifest = json(manifestPath);
    const apk = resolve(directory, manifest.apk);
    if (dirname(apk) !== directory || !apk.endsWith('.apk')) throw Error('Ruta de APK inválida.');
    validateManifest(manifest, appId, inspectApk(apk), hash(apk));
    checkHistory(manifest);
    const groups = cli(['appdistribution:groups:list']).result?.groups ?? [];
    const group = groups.find(g => g.name.endsWith(`/groups/${GROUP}`));
    if (!group || Number(group.testerCount ?? 0) < 1) throw Error(`Agregue probadores reales al grupo ${GROUP} antes de publicar.`);
    // firebase-tools 15.34 does not return a release in --json mode for this command.
    const output = run(process.execPath, [firebase, 'appdistribution:distribute', apk,
      '--app', appId, '--groups', GROUP, '--release-notes-file', notesPath,
      '--project', PROJECT, '--non-interactive'], true);
    const release = {
      firebaseConsoleUri: output.match(/https:\/\/console\.firebase\.google\.com\/[^\s\u001b]+/)?.[0],
      testingUri: output.match(/https:\/\/appdistribution\.firebase\.google\.com\/[^\s\u001b]+/)?.[0],
    };
    const record = { ...manifest, publishedAt: new Date().toISOString(), release };
    writeFileSync(ledgerPath, JSON.stringify([...previous(), record], null, 2) + '\n');
    console.log('Versión publicada.');
    // Only permanent console/tester links; omit short-lived signed download URLs.
    console.log(JSON.stringify({ firebaseConsoleUri: release?.firebaseConsoleUri,
      testingUri: release?.testingUri }, null, 2));
  } else throw Error('Use prepare o publish.');
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { main(); } catch (error) { console.error(`Distribución: ${error.message}`); process.exitCode = 1; }
}
