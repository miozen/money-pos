import test from 'node:test';
import assert from 'node:assert/strict';
import { handleConnectionTest, handleSync, hmacHex, sha256Hex, validateSnapshot, verifySignedRequest } from '../src/index.js';

const secret = 'MDEyMzQ1Njc4OWFiY2RlZg==';
const body = JSON.stringify({ storeId: 'store-a', snapshots: [] });
const now = 1_790_000_000_000;

async function signedRequest(overrides = {}) {
  const requestBody = overrides.body ?? body;
  const bodyHash = overrides.bodyHash ?? await sha256Hex(requestBody);
  const timestamp = overrides.timestamp ?? now;
  const nonce = 'nonce-123';
  const canonical = `POST\n/internal/v1/member-snapshots:batch\n${timestamp}\n${nonce}\n${bodyHash}`;
  const signature = overrides.signature ?? await hmacHex(secret, canonical);
  return new Request('https://cloud.example/internal/v1/member-snapshots:batch', {
    method: 'POST', body: requestBody,
    headers: {
      'X-Omnia-Timestamp': String(timestamp), 'X-Omnia-Nonce': nonce,
      'X-Omnia-Body-SHA256': bodyHash, 'X-Omnia-Signature': signature
    }
  });
}

test('verifies a correctly signed request without exposing a real key', async () => {
  const result = await verifySignedRequest(await signedRequest(), { OMNIA_SYNC_STORE_SECRETS_JSON: JSON.stringify({ 'store-a': [secret] }) }, now);
  assert.equal(result.storeId, 'store-a');
  assert.equal(result.body, body);
});

test('rejects changed body content and expired timestamps', async () => {
  const badHash = await verifySignedRequest(await signedRequest({ bodyHash: '0'.repeat(64) }), { OMNIA_SYNC_STORE_SECRETS_JSON: JSON.stringify({ 'store-a': secret }) }, now);
  assert.equal(badHash.status, 401);
  const expired = await verifySignedRequest(await signedRequest({ timestamp: now - 300_001 }), { OMNIA_SYNC_STORE_SECRETS_JSON: JSON.stringify({ 'store-a': secret }) }, now);
  assert.equal(expired.status, 401);
});

test('requires transformed snapshot material and rejects raw phone data', () => {
  assert.equal(validateSnapshot({ memberId: 1, version: 1, operation: 'UPSERT', sourceUpdatedAt: now, snapshot: {}, phone: '13800138000' }), 'raw phone must not be uploaded');
  assert.equal(validateSnapshot({ memberId: 1, version: 1, operation: 'UPSERT', sourceUpdatedAt: now, snapshot: { phone: '13800138000' } }), 'raw phone must not be uploaded');
  assert.equal(validateSnapshot({ memberId: 1, version: 1, operation: 'TOMBSTONE', sourceUpdatedAt: now }), null);
});

test('connection probe verifies the same signature without writing a snapshot', async () => {
  const db = { prepare: (sql) => ({ first: async () => { assert.equal(sql, 'SELECT 1'); } }) };
  const request = new Request('https://cloud.example/internal/v1/member-snapshots:test', {
    method: 'POST', body: JSON.stringify({ storeId: 'store-a' }),
    headers: await signedHeaders('/internal/v1/member-snapshots:test', JSON.stringify({ storeId: 'store-a' }))
  });
  const response = await handleConnectionTest(request, { DB: db, OMNIA_SYNC_STORE_SECRETS_JSON: JSON.stringify({ 'store-a': secret }) }, now);
  assert.deepEqual(await response.json(), { ok: true });
});

test('snapshot and audit persistence are sent to one D1 batch', async () => {
  const statements = [];
  const db = {
    prepare: (sql) => ({
      bind: (...args) => ({ sql, args, run: async () => ({ success: true }) })
    }),
    batch: async (items) => { statements.push(...items); return items.map(() => ({ success: true })); }
  };
  const payload = JSON.stringify({
    storeId: 'store-a', snapshots: [{ memberId: 7, version: 3, operation: 'UPSERT', sourceUpdatedAt: now, snapshot: { name: '张三' } }]
  });
  const response = await handleSync(await signedRequest({ body: payload }), { DB: db, OMNIA_SYNC_STORE_SECRETS_JSON: JSON.stringify({ 'store-a': secret }) }, now);
  assert.deepEqual(await response.json(), { accepted: 1 });
  assert.equal(statements.length, 2);
  assert.match(statements[0].sql, /INSERT INTO member_snapshots/);
  assert.match(statements[1].sql, /INSERT INTO sync_audit/);
});

async function signedHeaders(path, payload) {
  const bodyHash = await sha256Hex(payload);
  const nonce = 'nonce-connection';
  const canonical = `POST\n${path}\n${now}\n${nonce}\n${bodyHash}`;
  return {
    'X-Omnia-Timestamp': String(now), 'X-Omnia-Nonce': nonce,
    'X-Omnia-Body-SHA256': bodyHash, 'X-Omnia-Signature': await hmacHex(secret, canonical)
  };
}
