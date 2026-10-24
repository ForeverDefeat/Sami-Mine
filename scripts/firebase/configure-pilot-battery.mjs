// One-time pilot bootstrap through the same client Security Rules as the Android administrator.
// It never replaces an existing draft or active configuration and never creates instrument approvals.
import { readFile } from 'node:fs/promises';
import { initializeApp, deleteApp } from 'firebase/app';
import { getAuth, signInWithEmailAndPassword, signOut } from 'firebase/auth';
import { getFirestore, doc, runTransaction, terminate } from 'firebase/firestore';

const file = process.argv[2];
if (!file) throw Error('Use node scripts/firebase/configure-pilot-battery.mjs archivo-local-de-cuentas.json');
const google = JSON.parse(await readFile('app/google-services.json', 'utf8'));
const credentials = JSON.parse(await readFile(file, 'utf8'));
if (google.project_info.project_id !== 'samimine-dev' || credentials.project !== 'samimine-dev') throw Error('Solo samimine-dev está autorizado.');
const account = credentials.accounts.find(a => a.uid === 'pilot-admin');
if (!account) throw Error('No existe la credencial administrativa del piloto.');
const client = google.client.find(c => c.client_info.android_client_info.package_name === 'dev.studios.samimine');
const app = initializeApp({ apiKey: client.api_key[0].current_key, projectId: 'samimine-dev', appId: client.client_info.mobilesdk_app_id });
const auth = getAuth(app);
const db = getFirestore(app);
try {
  await signInWithEmailAndPassword(auth, account.email, account.password);
  const organizationId = 'pilot-mine';
  const root = `organizations/${organizationId}`;
  const draft = doc(db, `${root}/batteryDrafts/default`);
  const active = doc(db, `${root}/batterySettings/active`);
  const modules = ['PVT', 'FLANKER', 'GO_NO_GO', 'VISUAL_SEARCH', 'DSST', 'N_BACK'];
  const protocols = { PVT: 'demo-pvt-v1', FLANKER: 'demo-flanker-v2', GO_NO_GO: 'demo-gonogo-v2',
    VISUAL_SEARCH: 'demo-visual-search-v1', DSST: 'experimental-dsst-v1', N_BACK: 'experimental-1back-v1' };
  const created = await runTransaction(db, async tx => {
    const current = await tx.get(active);
    const existingDraft = await tx.get(draft);
    if (current.exists() || existingDraft.exists()) return false;
    const name = 'Batería cognitiva del piloto';
    tx.set(draft, { name, modules, revision: 1, schemaVersion: 1 });
    tx.set(doc(db, `${root}/batteryVersions/1`), { id: 'default', name, version: 1, organizationId, modules, protocols, schemaVersion: 1 });
    tx.set(active, { version: 1 });
    return true;
  });
  console.log(created ? 'Batería cognitiva inicial publicada: seis módulos; cuestionarios pendientes de contenido autorizado.' :
    'Ya existe una configuración o borrador; se conservó sin cambios.');
} finally {
  await signOut(auth); await terminate(db); await deleteApp(app);
}
