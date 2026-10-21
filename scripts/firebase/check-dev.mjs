// Read-only cloud smoke check with the five provisioned fictitious accounts; no credentials are logged.
import { readFile } from 'node:fs/promises';
import { initializeApp, deleteApp } from 'firebase/app';
import { getAuth, signInWithEmailAndPassword, signOut } from 'firebase/auth';
import { getFirestore, doc, getDocFromServer, collection, query, where, orderBy, limit, getDocsFromServer, documentId, terminate } from 'firebase/firestore';
import assert from 'node:assert/strict';

const credentialPath = process.argv[2];
if (!credentialPath) throw Error('Pass the local pilot-accounts file path.');
const accounts = JSON.parse(await readFile(credentialPath, 'utf8'));
if (accounts.project !== 'samimine-dev') throw Error('Only samimine-dev is allowed.');
const config = JSON.parse(await readFile('app/google-services.json', 'utf8'));
assert.equal(config.project_info.project_id, 'samimine-dev');
const client = config.client.find(item => item.client_info.android_client_info.package_name === 'dev.studios.samimine');
const app = initializeApp({ projectId: 'samimine-dev', apiKey: client.api_key[0].current_key, appId: client.client_info.mobilesdk_app_id }, 'cloud-read-check');
const auth = getAuth(app);
const db = getFirestore(app);
async function readyQuery(operation) {
  for (let attempt = 0; ; attempt++) {
    try { return await operation(); }
    catch (error) {
      if (error.code !== 'failed-precondition' || !error.message.includes('currently building') || attempt >= 11) throw error;
      if (attempt === 0) console.log('An index is still building; waiting before verifying that query.');
      await new Promise(resolve => setTimeout(resolve, 10_000));
    }
  }
}
try {
  for (const account of accounts.accounts) {
    await signInWithEmailAndPassword(auth, account.email, account.password);
    const profile = await getDocFromServer(doc(db, `users/${account.uid}`));
    assert.equal(profile.get('active'), true);
    assert.equal(profile.get('uid'), account.uid);
    const role = profile.get('role');
    if (role === 'WELLBEING') {
      const workers = await getDocsFromServer(query(collection(db, 'users'), where('organizationId', '==', 'pilot-mine'),
        where('role', '==', 'WORKER'), where('active', '==', true), where('wellbeingUid', '==', account.uid), orderBy(documentId()), limit(25)));
      assert.deepEqual(workers.docs.map(item => item.id).sort(), ['pilot-worker-a', 'pilot-worker-b']);
      await assert.rejects(getDocFromServer(doc(db, 'users/pilot-worker-c')), error => error.code === 'permission-denied');
    }
    if (role === 'ADMIN') {
      const workers = await getDocsFromServer(query(collection(db, 'users'), where('organizationId', '==', 'pilot-mine'),
        where('role', '==', 'WORKER'), orderBy(documentId()), limit(25)));
      assert.equal(workers.size, 3);
      await readyQuery(() => getDocsFromServer(query(collection(db, 'users/pilot-worker-a/tracking'), orderBy('startedAtMillis', 'desc'), orderBy(documentId()), limit(25))));
      await assert.rejects(getDocFromServer(doc(db, 'users/pilot-worker-a/results/connection-check')), error => error.code === 'permission-denied');
    }
    if (account.uid === 'pilot-worker-b') {
      await assert.rejects(getDocFromServer(doc(db, 'users/pilot-worker-a/results/connection-check')), error => error.code === 'permission-denied');
    }
    console.log(`${account.uid}: authenticated, active profile and ${role} permissions verified.`);
    await signOut(auth);
  }
  assert.equal(accounts.accounts.length, 5);
  console.log('Cloud smoke check passed: five accounts, role queries and cross-account denials. No evaluations were created.');
} finally {
  await signOut(auth).catch(() => {});
  await terminate(db);
  await deleteApp(app);
}
