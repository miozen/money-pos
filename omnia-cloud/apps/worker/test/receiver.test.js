import test from 'node:test';
import assert from 'node:assert/strict';
import { hmacHex, sha256Hex, validateSnapshot, verifySignedRequest } from '../src/index.js';

const secret = 'MDEyMzQ1Njc4OWFiY2RlZg==';
const body = JSON.stringify({ storeId: 'store-a', snapshots: [] });
const now = 1_790_000_000_000;

async function signedRequest(overrides = {}) {
  const bodyHash = overrides.bodyHash ?? await sha256Hex(body);
  const timestamp = overrides.timestamp ?? now;
  const nonce = 'nonce-123';
  const canonical = `POST\n/internal/v1/member-snapshots:batch\n${timestamp}\n${nonce}\n${bodyHash}`;
  const signature = overrides.signature ?? await hmacHex(secret, canonical);
  return new Request('https://cloud.example/internal/v1/member-snapshots:batch', {
    method: 'POST', body,
    headers: {
      'X-Omnia-Key-Id': 'key-a', 'X-Omnia-Timestamp': String(timestamp), 'X-Omnia-Nonce': nonce,
      'X-Omnia-Body-SHA256': bodyHash, 'X-Omnia-Signature': signature
    }
  });
}

test('verifies a correctly signed request without exposing a real key', async () => {
  const result = await verifySignedRequest(await signedRequest(), { OMNIA_SYNC_KEYS_JSON: JSON.stringify({ 'key-a': secret }) }, now);
  assert.equal(result.keyId, 'key-a');
  assert.equal(result.body, body);
});

test('rejects changed body content and expired timestamps', async () => {
  const badHash = await verifySignedRequest(await signedRequest({ bodyHash: '0'.repeat(64) }), { OMNIA_SYNC_KEYS_JSON: JSON.stringify({ 'key-a': secret }) }, now);
  assert.equal(badHash.status, 401);
  const expired = await verifySignedRequest(await signedRequest({ timestamp: now - 300_001 }), { OMNIA_SYNC_KEYS_JSON: JSON.stringify({ 'key-a': secret }) }, now);
  assert.equal(expired.status, 401);
});

test('requires transformed snapshot material and rejects raw phone data', () => {
  assert.equal(validateSnapshot({ memberId: 1, version: 1, operation: 'UPSERT', sourceUpdatedAt: now, snapshot: {}, phone: '13800138000' }), 'raw phone must not be uploaded');
  assert.equal(validateSnapshot({ memberId: 1, version: 1, operation: 'UPSERT', sourceUpdatedAt: now, snapshot: { phone: '13800138000' } }), 'raw phone must not be uploaded');
  assert.equal(validateSnapshot({ memberId: 1, version: 1, operation: 'TOMBSTONE', sourceUpdatedAt: now }), null);
});
