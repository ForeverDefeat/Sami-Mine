import test from 'node:test';
import assert from 'node:assert/strict';
import { appIdentity, parseArgs, validateVersion, validateManifest, validateHistory, PROJECT, PACKAGE } from './distribution.mjs';

const config = () => ({ project_info: { project_id: PROJECT }, client: [{ client_info: {
  android_client_info: { package_name: PACKAGE }, mobilesdk_app_id: '1:123:android:abc',
} }] });

test('selects only the matching Firebase Android client', () => {
  const value = config();
  value.client.unshift({ client_info: { android_client_info: { package_name: 'another.app' } } });
  assert.equal(appIdentity(value), '1:123:android:abc');
  value.project_info.project_id = 'wrong-project';
  assert.throws(() => appIdentity(value));
  assert.throws(() => appIdentity({ ...config(), client: [] }));
  const duplicate = config(); duplicate.client.push(duplicate.client[0]);
  assert.throws(() => appIdentity(duplicate));
});

test('rejects invalid versions and malformed command arguments', () => {
  for (const code of ['0', '-1', '1.2', '2100000001', '1 & echo x', undefined]) {
    assert.throws(() => validateVersion(code, '1.0'));
  }
  assert.throws(() => validateVersion('2', ' '));
  assert.throws(() => validateVersion('2', 'a\nb'));
  validateVersion('2', '1.0-pilot');
  assert.throws(() => parseArgs(['--version-code']));
  assert.throws(() => parseArgs(['--unknown', 'x']));
  assert.throws(() => parseArgs(['--version-code', '2', '--version-code', '3']));
});

test('blocks changed binaries, project, app identity, versions and certificate before upload', () => {
  const info = { packageName: PACKAGE, versionCode: 2, versionName: '1.0', certificateSha256: 'cert' };
  const manifest = { project: PROJECT, appId: '1:123:android:abc', group: 'samimine-piloto', sha256: 'hash', ...info };
  validateManifest(manifest, manifest.appId, info, 'hash');
  assert.throws(() => validateManifest(manifest, manifest.appId, info, 'tampered'));
  for (const key of ['project', 'appId', 'group', ...Object.keys(info)]) {
    assert.throws(() => validateManifest({ ...manifest, [key]: 'changed' }, manifest.appId, info, 'hash'));
  }
});

test('requires increasing versions and the original certificate across deliveries', () => {
  const history = [{ versionCode: 2, certificateSha256: 'original' }];
  assert.throws(() => validateHistory({ versionCode: 2, certificateSha256: 'original' }, history));
  assert.throws(() => validateHistory({ versionCode: 1, certificateSha256: 'original' }, history));
  assert.throws(() => validateHistory({ versionCode: 3, certificateSha256: 'different' }, history));
  validateHistory({ versionCode: 3, certificateSha256: 'original' }, history);
});
