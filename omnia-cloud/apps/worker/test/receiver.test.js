import test from 'node:test';
import assert from 'node:assert/strict';
import { handleConnectionTest, handleMemberDetail, handleMemberSearch, handleSync, hmacHex, sha256Hex, validateSnapshot, verifySignedRequest } from '../src/index.js';

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
  assert.equal(validateSnapshot({ memberId: '1', version: '1', operation: 'UPSERT', sourceUpdatedAt: now, snapshot: {}, phone: '13800138000' }), 'raw phone must not be uploaded');
  assert.equal(validateSnapshot({ memberId: '1', version: '1', operation: 'UPSERT', sourceUpdatedAt: now, snapshot: { phone: '13800138000' } }), 'raw phone must not be uploaded');
  assert.equal(validateSnapshot({ memberId: '1', version: '1', operation: 'TOMBSTONE', sourceUpdatedAt: now }), null);
  assert.equal(validateSnapshot({ memberId: '2102313829312065536', version: '1', operation: 'TOMBSTONE', sourceUpdatedAt: now }), null);
  assert.equal(validateSnapshot({ memberId: '9223372036854775808', version: '1', operation: 'TOMBSTONE', sourceUpdatedAt: now }), 'snapshot fields are invalid');
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
    storeId: 'store-a', snapshots: [{ memberId: '2102313829312065536', version: '3', operation: 'UPSERT', sourceUpdatedAt: now, snapshot: { name: '张三' } }]
  });
  const response = await handleSync(await signedRequest({ body: payload }), { DB: db, OMNIA_SYNC_STORE_SECRETS_JSON: JSON.stringify({ 'store-a': secret }) }, now);
  assert.deepEqual(await response.json(), { accepted: 1 });
  assert.equal(statements.length, 2);
  assert.match(statements[0].sql, /INSERT INTO member_snapshots/);
  assert.equal(statements[0].args[1], '2102313829312065536');
  assert.match(statements[1].sql, /INSERT INTO sync_audit/);
});

test('member query uses the fixed store, search HMAC, and never returns search fields', async () => {
  const searchSecret = 'MDEyMzQ1Njc4OWFiY2RlZg==';
  let queryStatement;
  const db = {
    prepare: (sql) => ({
      bind: (...args) => ({
        all: async () => { queryStatement = { sql, args }; return { results: [memberRow()] }; }
      })
    })
  };
  const request = queryRequest('/api/v1/members/search', { query: '13800138000' });
  const response = await handleMemberSearch(request, {
    DB: db, OMNIA_QUERY_PASSWORD: 'only-me', OMNIA_QUERY_STORE_ID: 'store-01-test', OMNIA_SEARCH_HMAC_SECRET_BASE64: searchSecret
  });
  const body = await response.json();
  assert.equal(response.status, 200);
  assert.equal(queryStatement.args[0], 'store-01-test');
  assert.equal(queryStatement.args[1], await hmacHex(searchSecret, '13800138000'));
  assert.equal(body.members[0].phoneMasked, '138****8000');
  assert.doesNotMatch(JSON.stringify(body), /phoneHmac|phoneLast4Hmac|nameNormalized/);
});

test('member query rejects bad password and hides deleted snapshots', async () => {
  const unauthorized = await handleMemberSearch(new Request('https://cloud.example/api/v1/members/search', {
    method: 'POST', body: JSON.stringify({ query: '张三' }), headers: { authorization: 'Basic ' + btoa('omnia:wrong') }
  }), { OMNIA_QUERY_PASSWORD: 'only-me', OMNIA_QUERY_STORE_ID: 'store-01', OMNIA_SEARCH_HMAC_SECRET_BASE64: secret });
  assert.equal(unauthorized.status, 401);
  let sql;
  const response = await handleMemberSearch(queryRequest('/api/v1/members/search', { query: '张三' }), {
    OMNIA_QUERY_PASSWORD: 'only-me', OMNIA_QUERY_STORE_ID: 'store-01', OMNIA_SEARCH_HMAC_SECRET_BASE64: secret,
    DB: { prepare: (value) => ({ bind: (...args) => ({ all: async () => { sql = value; assert.equal(args[0], 'store-01'); return { results: [] }; } }) }) }
  });
  assert.equal(response.status, 200);
  assert.match(sql, /status = 'ACTIVE'/);
});

test('member detail preserves a large member ID and fixes the query partition', async () => {
  let args;
  const request = new Request('https://cloud.example/api/v1/members/2102313829312065536', {
    headers: { authorization: 'Basic ' + btoa('omnia:only-me') }
  });
  const response = await handleMemberDetail(request, {
    OMNIA_QUERY_PASSWORD: 'only-me', OMNIA_QUERY_STORE_ID: 'store-01-test', OMNIA_SEARCH_HMAC_SECRET_BASE64: secret,
    DB: { prepare: () => ({ bind: (...values) => ({ first: async () => { args = values; return memberRow(); } }) }) }
  }, '2102313829312065536');
  assert.equal(response.status, 200);
  assert.deepEqual(args, ['store-01-test', '2102313829312065536']);
  assert.equal((await response.json()).member.memberId, '2102313829312065536');
});

function queryRequest(path, body) {
  return new Request(`https://cloud.example${path}`, {
    method: 'POST', body: JSON.stringify(body),
    headers: { 'content-type': 'application/json', authorization: 'Basic ' + btoa('omnia:only-me') }
  });
}

function memberRow() {
  return {
    member_id: '2102313829312065536', name: '张三', phone_masked: '138****8000', source_updated_at: 1_790_000_000_000, synced_at: 1_790_000_000_001,
    snapshot_json: JSON.stringify({ memberCode: 'M001', memberType: 'VIP', balance: '12.34', couponBalance: '0', nameNormalized: '张三', phoneHmac: 'hidden', phoneLast4Hmac: 'hidden', brandLevels: { A: 'GOLD' }, unusedCoupons: [], quantityRights: [], amountRights: [], targetPlans: [] })
  };
}

async function signedHeaders(path, payload) {
  const bodyHash = await sha256Hex(payload);
  const nonce = 'nonce-connection';
  const canonical = `POST\n${path}\n${now}\n${nonce}\n${bodyHash}`;
  return {
    'X-Omnia-Timestamp': String(now), 'X-Omnia-Nonce': nonce,
    'X-Omnia-Body-SHA256': bodyHash, 'X-Omnia-Signature': await hmacHex(secret, canonical)
  };
}
