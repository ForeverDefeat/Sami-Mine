// Creates a local key only on explicit invocation; never replaces signing material.
import { existsSync, mkdirSync, readdirSync, writeFileSync } from 'node:fs';
import { resolve, join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomBytes } from 'node:crypto';
import { spawnSync } from 'node:child_process';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const directory = join(root, '.local/signing');
const properties = join(root, 'keystore.properties');
try {
  if (existsSync(properties) || (existsSync(directory) && readdirSync(directory).length)) {
    throw Error('Ya existe configuración o material de firma. Revíselo y reutilice la clave; no se sobrescribe.');
  }
  if (!process.env.JAVA_HOME) throw Error('Configure JAVA_HOME apuntando a su JDK.');
  mkdirSync(directory, { recursive: true });
  const password = randomBytes(32).toString('hex');
  const result = spawnSync(join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'keytool.exe' : 'keytool'), [
    '-genkeypair', '-keystore', join(directory, 'samimine-release.jks'), '-storetype', 'JKS',
    '-alias', 'samimine', '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000',
    '-dname', 'CN=SamiMine Distribution', '-storepass:env', 'SAMIMINE_SIGNING_PASSWORD',
    '-keypass:env', 'SAMIMINE_SIGNING_PASSWORD', '-noprompt',
  ], { env: { ...process.env, SAMIMINE_SIGNING_PASSWORD: password }, encoding: 'utf8' });
  if (result.error || result.status !== 0) throw Error('No se pudo generar la clave de firma. Revise JAVA_HOME.');
  writeFileSync(properties, `storeFile=.local/signing/samimine-release.jks\nstorePassword=${password}\nkeyAlias=samimine\nkeyPassword=${password}\n`,
    { flag: 'wx', mode: 0o600 });
  console.log('Firma local creada. Respalde .local/signing/samimine-release.jks y keystore.properties en almacenamiento seguro.');
} catch (error) { console.error(error.message); process.exitCode = 1; }
