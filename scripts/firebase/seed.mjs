import { initializeApp, applicationDefault } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore } from 'firebase-admin/firestore';
import { randomBytes } from 'node:crypto';
import { writeFile, mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';

const emulator = process.argv.includes('--emulator');
const project = emulator ? 'demo-samimine' : process.argv[process.argv.indexOf('--project') + 1];
if (project !== (emulator ? 'demo-samimine' : 'samimine-dev')) throw Error('Only the approved development project is allowed.');
if (emulator) {
  process.env.FIREBASE_AUTH_EMULATOR_HOST = '127.0.0.1:9099';
  process.env.FIRESTORE_EMULATOR_HOST = '127.0.0.1:8080';
} else {
  if (process.env.FIREBASE_AUTH_EMULATOR_HOST || process.env.FIRESTORE_EMULATOR_HOST) throw Error('Remove emulator environment variables for development provisioning.');
}
initializeApp({ projectId: project, ...(emulator ? {} : { credential: applicationDefault() }) });
const auth = getAuth();
const db = getFirestore();
const fixtures = [
  { uid: 'pilot-wellbeing', name: 'Bienestar de prueba', role: 'WELLBEING', wellbeingUid: null },
  { uid: 'pilot-admin', name: 'Administración de prueba', role: 'ADMIN', wellbeingUid: null },
  { uid: 'pilot-worker-a', name: 'Trabajador A', role: 'WORKER', wellbeingUid: 'pilot-wellbeing' },
  { uid: 'pilot-worker-b', name: 'Trabajador B', role: 'WORKER', wellbeingUid: 'pilot-wellbeing' },
  { uid: 'pilot-worker-c', name: 'Trabajador C', role: 'WORKER', wellbeingUid: null },
];
const credentials = [];
for (const fixture of fixtures) {
  const email = `${fixture.uid}@samimine.example`;
  let account;
  try { account = await auth.getUser(fixture.uid); }
  catch (error) { if (error.code !== 'auth/user-not-found') throw error; }
  if (!account) {
    const password = emulator ? 'Pilot-Emulator-2026!' : randomBytes(24).toString('base64url') + '!1aA';
    await auth.createUser({ uid: fixture.uid, email, password, displayName: fixture.name, emailVerified: false });
    credentials.push({ uid: fixture.uid, email, password });
  }
  // Do not overwrite assignments or reactivate accounts on a rerun.
  const profile = db.doc(`users/${fixture.uid}`);
  if (!(await profile.get()).exists) await profile.create({ ...fixture, organizationId: 'pilot-mine', active: true, area: 'Área de prueba', employment: 'Demostración' });
}
await mkdir('.local', { recursive: true });
if (credentials.length) {
  const file = resolve('.local', `pilot-accounts-${Date.now()}.json`);
  await writeFile(file, JSON.stringify({ project, accounts: credentials }, null, 2), { flag: 'wx', mode: 0o600 });
  console.log(`Created ${credentials.length} accounts. Credentials saved locally in ${file}; never commit or paste this file into logs.`);
} else console.log('Accounts already exist; no passwords or assignments were changed.');
