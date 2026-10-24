import { before, after, beforeEach, test } from 'node:test';
import { readFile } from 'node:fs/promises';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, collection, setDoc, updateDoc, getDoc, getDocs, query, where, orderBy, limit, writeBatch, serverTimestamp, deleteDoc } from 'firebase/firestore';

let env;
const profile = (uid, role = 'WORKER', extra = {}) => ({ uid, role, name: uid, organizationId: 'mine-a', active: true, area: 'Demo', employment: 'Demo', wellbeingUid: role === 'WORKER' ? 'well' : null, ...extra });
const result = (extra = {}) => ({ ownerUid: 'a', organizationId: 'mine-a', kind: 'PVT', title: 'Reacción', status: 'COMPLETED', startedAtMillis: 100,
  summaryJson: '{"metrics":{"responses":3}}', chunkCount: 1, complete: false, ...extra });
const db = uid => env.authenticatedContext(uid).firestore();
const resultPath = 'users/a/results/PVT_run';
const configurationRoot = 'organizations/mine-a';
const batteryDraft = (extra = {}) => ({ name: 'Batería de prueba', modules: ['PVT', 'DSST'], revision: 1, schemaVersion: 1, ...extra });
const batteryVersion = (extra = {}) => ({ id: 'default', name: 'Batería de prueba', version: 1,
  organizationId: 'mine-a', modules: ['PVT', 'DSST'], protocols: { PVT: 'demo-pvt-v1', DSST: 'experimental-dsst-v1' }, schemaVersion: 1, ...extra });
async function publishConfiguration(client, value = batteryVersion()) {
  const batch = writeBatch(client);
  batch.set(doc(client, `${configurationRoot}/batteryVersions/${value.version}`), value);
  batch.set(doc(client, `${configurationRoot}/batterySettings/active`), { version: value.version });
  return batch.commit();
}
before(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-samimine', firestore: { rules: await readFile('firestore.rules', 'utf8') } });
});
after(async () => { await env?.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async context => {
    const admin = context.firestore();
    for (const p of [profile('a'), profile('b'), profile('c', 'WORKER', { wellbeingUid: null }), profile('well', 'WELLBEING'), profile('other-well', 'WELLBEING'), profile('admin', 'ADMIN'), profile('foreign', 'WELLBEING', { organizationId: 'other' })])
      await setDoc(doc(admin, `users/${p.uid}`), p);
  });
});

test('only same-organization administrators edit drafts; revision conflicts and malformed modules fail', async () => {
  const path = `${configurationRoot}/batteryDrafts/default`;
  for (const uid of ['a', 'well', 'foreign', 'unknown']) await assertFails(setDoc(doc(db(uid), path), batteryDraft()));
  await assertSucceeds(setDoc(doc(db('admin'), path), batteryDraft()));
  await assertFails(getDoc(doc(db('a'), path)));
  await assertFails(setDoc(doc(db('admin'), path), batteryDraft()));
  for (const extra of [{ modules: [] }, { modules: ['PVT', 'PVT'] }, { modules: ['UNKNOWN'] },
    { modules: ['BORG'] }, { schemaVersion: 2 }, { name: '' }, { extra: true }, { revision: 5 }]) {
    await assertFails(setDoc(doc(db('admin'), path), batteryDraft({ revision: 2, ...extra })));
  }
  await assertSucceeds(setDoc(doc(db('admin'), path), batteryDraft({ revision: 2, modules: ['DSST', 'PVT'] })));
});

test('publication and active pointer are atomic, immutable, organization isolated and strictly versioned', async () => {
  const admin = db('admin');
  await assertSucceeds(setDoc(doc(admin, `${configurationRoot}/batteryDrafts/default`), batteryDraft()));
  await assertFails(setDoc(doc(admin, `${configurationRoot}/batteryVersions/1`), batteryVersion()));
  await assertFails(setDoc(doc(admin, `${configurationRoot}/batterySettings/active`), { version: 1 }));
  await assertSucceeds(publishConfiguration(admin));
  await assertSucceeds(getDoc(doc(db('a'), `${configurationRoot}/batteryVersions/1`)));
  await assertFails(getDoc(doc(db('foreign'), `${configurationRoot}/batteryVersions/1`)));
  await assertFails(updateDoc(doc(admin, `${configurationRoot}/batteryVersions/1`), { name: 'Changed' }));
  await assertFails(deleteDoc(doc(admin, `${configurationRoot}/batteryVersions/1`)));
  await assertFails(publishConfiguration(admin, batteryVersion({ version: 3 })));
  await assertFails(publishConfiguration(db('a'), batteryVersion({ version: 2 })));
  await assertFails(publishConfiguration(admin, batteryVersion({ version: 2, protocols: { PVT: 'unknown', DSST: 'experimental-dsst-v1' } })));
  await assertSucceeds(publishConfiguration(admin, batteryVersion({ version: 2 })));
  await assertFails(setDoc(doc(admin, `${configurationRoot}/batterySettings/active`), { version: 1 }));
});

test('unapproved questionnaire publication is denied even when the caller bypasses app validation', async () => {
  const admin = db('admin');
  await assertSucceeds(setDoc(doc(admin, `${configurationRoot}/batteryDrafts/default`), batteryDraft({ modules: ['KSS', 'PVT'] })));
  await assertFails(publishConfiguration(admin, batteryVersion({ modules: ['KSS', 'PVT'], protocols: { KSS: 'invented', PVT: 'demo-pvt-v1' } })));
  await assertFails(setDoc(doc(admin, 'instrument_catalog/KSS'), { approved: true, version: 'invented' }));
});
async function draft() { await assertSucceeds(setDoc(doc(db('a'), resultPath), result())); }
async function finalize() {
  const client = db('a');
  await assertSucceeds(setDoc(doc(client, `${resultPath}/chunks/0`), { index: 0, json: '[{"index":1}]' }));
  const batch = writeBatch(client);
  batch.update(doc(client, resultPath), { complete: true });
  batch.set(doc(client, 'users/a/tracking/PVT_run'), { ownerUid: 'a', organizationId: 'mine-a', kind: 'PVT', status: 'COMPLETED', startedAtMillis: 100, receivedAt: serverTimestamp() });
  await assertSucceeds(batch.commit());
}
test('unprofiled and anonymous accounts cannot access results', async () => {
  await draft();
  await assertFails(getDoc(doc(db('unknown'), resultPath)));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), resultPath)));
  await assertFails(setDoc(doc(db('unknown'), 'users/unknown'), profile('unknown', 'ADMIN')));
});
test('owner isolated; wellbeing only sees complete assigned evaluations', async () => {
  await draft();
  await assertSucceeds(getDoc(doc(db('a'), resultPath)));
  for (const uid of ['b', 'well', 'other-well', 'admin', 'foreign']) await assertFails(getDoc(doc(db(uid), resultPath)));
  await finalize();
  await assertSucceeds(getDoc(doc(db('well'), resultPath)));
  await assertSucceeds(getDocs(query(collection(db('well'), 'users/a/results'), where('complete', '==', true), orderBy('startedAtMillis', 'desc'), orderBy('__name__'), limit(25))));
  await assertFails(getDocs(query(collection(db('well'), 'users/a/results'), limit(25))));
  await assertFails(getDoc(doc(db('admin'), `${resultPath}/chunks/0`)));
});
test('administrator sees tracking only; hidden fields cannot be injected', async () => {
  await draft(); await finalize();
  await assertSucceeds(getDoc(doc(db('admin'), 'users/a/tracking/PVT_run')));
  await assertSucceeds(getDocs(query(collection(db('admin'), 'users/a/tracking'), orderBy('startedAtMillis', 'desc'), orderBy('__name__'), limit(25))));
  await assertFails(setDoc(doc(db('a'), 'users/a/tracking/fake'), { ownerUid: 'a', metrics: 'secret' }));
  await assertFails(updateDoc(doc(db('a'), 'users/a/tracking/PVT_run'), { summaryJson: 'secret' }));
});
test('owner cannot elevate roles or forge ownership, organizations or draft completion', async () => {
  await assertFails(updateDoc(doc(db('a'), 'users/a'), { role: 'ADMIN' }));
  await assertFails(setDoc(doc(db('a'), resultPath), result({ ownerUid: 'b' })));
  await assertFails(setDoc(doc(db('a'), resultPath), result({ organizationId: 'other' })));
  await assertFails(setDoc(doc(db('a'), resultPath), result({ complete: true })));
  await assertFails(setDoc(doc(db('b'), resultPath), result()));
  await assertFails(setDoc(doc(db('a'), resultPath), result({ extra: 'unexpected' })));
});
test('immutable payload and indexed chunks make retries safe', async () => {
  await draft(); await draft();
  await assertFails(setDoc(doc(db('a'), `${resultPath}/chunks/1`), { index: 1, json: '[]' }));
  await assertFails(setDoc(doc(db('a'), `${resultPath}/chunks/0`), { index: 0, json: '[]', extra: true }));
  await finalize();
  await assertSucceeds(setDoc(doc(db('a'), `${resultPath}/chunks/0`), { index: 0, json: '[{"index":1}]' }));
  await assertFails(updateDoc(doc(db('a'), resultPath), { summaryJson: '{}' }));
  await assertFails(updateDoc(doc(db('a'), resultPath), { complete: false }));
  await assertFails(deleteDoc(doc(db('a'), resultPath)));
});
test('administrator can update worker operational fields but cannot change roles or self', async () => {
  await assertSucceeds(updateDoc(doc(db('admin'), 'users/a'), { name: 'Nombre nuevo', area: 'Otra área', active: false, wellbeingUid: null }));
  for (const data of [{ role: 'ADMIN' }, { uid: 'other' }, { organizationId: 'other' }, { extra: true }, { name: '' }, { active: 'true' }, { wellbeingUid: 'foreign' }, { wellbeingUid: 'admin' }])
    await assertFails(updateDoc(doc(db('admin'), 'users/b'), data));
  await assertFails(updateDoc(doc(db('admin'), 'users/admin'), { active: false }));
  await assertFails(updateDoc(doc(db('well'), 'users/a'), { active: true }));
});
test('queries are scoped and bounded for each role', async () => {
  const workers = client => query(collection(client, 'users'), where('organizationId', '==', 'mine-a'), where('role', '==', 'WORKER'), orderBy('__name__'), limit(25));
  await assertSucceeds(getDocs(workers(db('admin'))));
  await assertFails(getDocs(workers(db('well'))));
  await assertSucceeds(getDocs(query(collection(db('well'), 'users'), where('organizationId', '==', 'mine-a'), where('role', '==', 'WORKER'), where('active', '==', true), where('wellbeingUid', '==', 'well'), orderBy('__name__'), limit(25))));
  await assertSucceeds(getDocs(query(collection(db('admin'), 'users'), where('organizationId', '==', 'mine-a'), where('role', '==', 'WELLBEING'), where('active', '==', true), limit(50))));
  await assertFails(getDocs(query(collection(db('admin'), 'users'), where('role', '==', 'WORKER'), limit(25))));
  await assertFails(getDocs(query(collection(db('admin'), 'users'), where('organizationId', '==', 'mine-a'), where('role', '==', 'WORKER'))));
});
test('revoking assignment or disabling owner immediately blocks remote access', async () => {
  await draft(); await finalize();
  await assertSucceeds(updateDoc(doc(db('admin'), 'users/a'), { wellbeingUid: null }));
  await assertFails(getDoc(doc(db('well'), resultPath)));
  await assertSucceeds(updateDoc(doc(db('admin'), 'users/a'), { active: false }));
  await assertFails(getDoc(doc(db('a'), resultPath)));
  await assertFails(setDoc(doc(db('a'), 'users/a/results/PVT_new'), result()));
  await assertSucceeds(getDoc(doc(db('admin'), 'users/a/tracking/PVT_run')));
});
test('larger deliveries use bounded write batches and cannot close without tracking', async () => {
  const client = db('a');
  await assertSucceeds(setDoc(doc(client, resultPath), result({ chunkCount: 13 })));
  await assertFails(updateDoc(doc(client, resultPath), { complete: true }));
  for (let start = 0; start < 13; start += 5) {
    const batch = writeBatch(client);
    for (let index = start; index < Math.min(start + 5, 13); index++) batch.set(doc(client, `${resultPath}/chunks/${index}`), { index, json: '[]' });
    await assertSucceeds(batch.commit());
  }
  const complete = writeBatch(client);
  complete.update(doc(client, resultPath), { complete: true });
  complete.set(doc(client, 'users/a/tracking/PVT_run'), { ownerUid: 'a', organizationId: 'mine-a', kind: 'PVT', status: 'COMPLETED', startedAtMillis: 100, receivedAt: serverTimestamp() });
  await assertSucceeds(complete.commit());
  await assertSucceeds(getDocs(query(collection(db('well'), `${resultPath}/chunks`), orderBy('index'), limit(100))));
});
