// Development provisioning only: reuse the explicitly signed-in CLI account via temporary ADC.
// No administrative credential is written into the workspace or printed to logs.
import { createRequire } from 'node:module';
import { mkdtemp, writeFile, unlink, rmdir } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

if (process.argv[process.argv.indexOf('--project') + 1] !== 'samimine-dev') throw Error('Only samimine-dev is allowed.');
if (process.env.GOOGLE_APPLICATION_CREDENTIALS) throw Error('ADC is already configured. Use seed:dev with your existing credentials.');
if (process.env.FIREBASE_AUTH_EMULATOR_HOST || process.env.FIRESTORE_EMULATOR_HOST) throw Error('Remove emulator environment variables first.');
const require = createRequire(import.meta.url);
// This development helper is tied to the CLI version pinned in package-lock.json.
const { getGlobalDefaultAccount } = require('firebase-tools/lib/auth');
const { clientId, clientSecret } = require('firebase-tools/lib/api');
const account = getGlobalDefaultAccount();
if (!account?.tokens?.refresh_token) throw Error('Sign in to the Firebase CLI before provisioning.');
const directory = await mkdtemp(join(tmpdir(), 'samimine-admin-'));
const credentialPath = join(directory, 'adc.json');
try {
  await writeFile(credentialPath, JSON.stringify({ type: 'authorized_user', client_id: clientId(), client_secret: clientSecret(),
    refresh_token: account.tokens.refresh_token, quota_project_id: 'samimine-dev' }), { flag: 'wx', mode: 0o600 });
  process.env.GOOGLE_APPLICATION_CREDENTIALS = credentialPath;
  await import('./seed.mjs');
} finally {
  delete process.env.GOOGLE_APPLICATION_CREDENTIALS;
  await unlink(credentialPath).catch(() => {});
  await rmdir(directory).catch(() => {});
}
