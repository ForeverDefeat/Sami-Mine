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
