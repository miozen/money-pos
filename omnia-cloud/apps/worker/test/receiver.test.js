import test from 'node:test';
import assert from 'node:assert/strict';
import worker, { handleConnectionTest, handleMemberDetail, handleMemberSearch, handleQuerySession, handleSync, handleTelegramWebhook, hmacHex, sha256Hex, telegramMemberText, validateSnapshot, verifySignedRequest } from '../src/index.js';

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
  assert.deepEqual(body.members[0].brandLevelDescriptions, { 蓝月: '皇冠' });
  assert.equal(body.members[0].memberCode, undefined);
  assert.doesNotMatch(JSON.stringify(body), /phoneHmac|phoneLast4Hmac|nameNormalized/);
});

test('query session validates the password before showing the search screen', async () => {
  const env = { OMNIA_QUERY_PASSWORD: 'only-me', OMNIA_QUERY_STORE_ID: 'store-01', OMNIA_SEARCH_HMAC_SECRET_BASE64: secret };
  const authorized = await handleQuerySession(new Request('https://cloud.example/api/v1/query-session', {
    headers: { authorization: 'Basic ' + btoa('omnia:only-me') }
  }), env);
  assert.deepEqual(await authorized.json(), { ok: true });
  const unauthorized = await handleQuerySession(new Request('https://cloud.example/api/v1/query-session'), env);
  assert.equal(unauthorized.status, 401);
});

test('query page uses the POS asset labels and totals remaining reduction coupons', async () => {
  const response = await worker.fetch(new Request('https://cloud.example/'));
  const page = await response.text();
  assert.equal(response.status, 200);
  assert.match(page, /会员余额/);
  assert.match(page, /会员券/);
  assert.match(page, /剩余满减券/);
  assert.match(page, /寄存商品/);
  assert.match(page, /couponCount\(member\.unusedCoupons\)/);
  assert.doesNotMatch(page, /优惠余额/);
  assert.doesNotMatch(page, /可用优惠券/);
  assert.doesNotMatch(page, /次数权益/);
});

test('Telegram webhook only queries for its configured private user and formats the existing projection', async () => {
  const calls = [];
  const env = telegramEnvironment(calls);
  const response = await handleTelegramWebhook(telegramRequest({
    message: { message_id: 1, chat: { id: 42, type: 'private' }, from: { id: 7 }, text: '13800138000' }
  }), env);
  assert.equal(response.status, 200);
  assert.equal(calls.length, 1);
  assert.match(calls[0].url, /botbot-token\/sendMessage$/);
  assert.match(calls[0].body.text, /会员余额：¥12\.34/);
  assert.match(calls[0].body.text, /会员券：¥0\.00/);
  assert.match(calls[0].body.text, /剩余满减券：0 张/);

  const unauthorized = await handleTelegramWebhook(telegramRequest({
    message: { message_id: 2, chat: { id: 99, type: 'private' }, from: { id: 8 }, text: '13800138000' }
  }), env);
  assert.equal(unauthorized.status, 200);
  assert.equal(calls.length, 1);
});

test('Telegram webhook rejects unsigned and group updates, and offers callback member selection', async () => {
  const calls = [];
  const env = telegramEnvironment(calls);
  const unsigned = await handleTelegramWebhook(new Request('https://cloud.example/telegram/webhook', {
    method: 'POST', body: '{}', headers: { 'content-type': 'application/json' }
  }), env);
  assert.equal(unsigned.status, 401);

  const group = await handleTelegramWebhook(telegramRequest({
    message: { message_id: 1, chat: { id: -1, type: 'group' }, from: { id: 7 }, text: '张三' }
  }), env);
  assert.equal(group.status, 200);
  assert.equal(calls.length, 0);

  env.DB.prepare = () => ({ bind: () => ({ all: async () => ({ results: [memberRow(), { ...memberRow(), member_id: '2', name: '李四' }] }) }) });
  const multiple = await handleTelegramWebhook(telegramRequest({
    message: { message_id: 3, chat: { id: 42, type: 'private' }, from: { id: 7 }, text: '张' }
  }), env);
  assert.equal(multiple.status, 200);
  assert.equal(calls[0].body.reply_markup.inline_keyboard.length, 2);
  assert.equal(calls[0].body.reply_markup.inline_keyboard[0][0].callback_data, 'member:2102313829312065536');

  calls.length = 0;
  env.DB.prepare = () => ({ bind: () => ({ first: async () => memberRow() }) });
  const callback = await handleTelegramWebhook(telegramRequest({
    callback_query: { id: 'callback-1', data: 'member:2102313829312065536', from: { id: 7 }, message: { chat: { id: 42, type: 'private' } } }
  }), env);
  assert.equal(callback.status, 200);
  assert.equal(calls.length, 2);
  assert.match(calls[0].url, /answerCallbackQuery$/);
  assert.match(calls[1].body.text, /手机号：138\*\*\*\*8000/);
});

test('Telegram formatter uses the same asset wording as the webpage', () => {
  const member = { ...JSON.parse(memberRow().snapshot_json), name: '张三', phoneMasked: '138****8000', syncedAt: 1_790_000_000_001,
    quantityRights: [{ goodsName: '寄存商品', remainingQuantity: 90 }], amountRights: [{ tierName: '金额权益', remainingAmount: 1080 }],
    targetPlans: [{ targetTierName: '目标权益', progressAmount: 240, targetAmount: 1080, remainingAmount: 840 }] };
  const text = telegramMemberText(member);
  assert.match(text, /👤 张三/);
  assert.match(text, /🏷️ 品牌等级/);
  assert.match(text, /💰 会员资产/);
  assert.match(text, /会员余额：¥12\.34/);
  assert.match(text, /会员券：¥0\.00/);
  assert.match(telegramMemberText(member), /剩余满减券：0 张/);
  assert.match(text, /寄存商品\n• 寄存商品：剩余 90/);
  assert.doesNotMatch(text, /次数权益|剩余 90 次/);
  assert.match(text, /金额权益\n• 金额权益：剩余 ¥1,080\.00/);
  assert.match(text, /目标权益\n• 目标权益\n  进度：¥240\.00 \/ ¥1,080\.00\n  剩余：¥840\.00/);
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
    snapshot_json: JSON.stringify({ memberCode: 'M001', memberType: 'VIP', balance: '12.34', couponBalance: '0', nameNormalized: '张三', phoneHmac: 'hidden', phoneLast4Hmac: 'hidden', brandLevels: { A: 'GOLD' }, brandLevelDescriptions: { 蓝月: '皇冠' }, unusedCoupons: [], quantityRights: [], amountRights: [], targetPlans: [] })
  };
}

function telegramEnvironment(calls) {
  return {
    DB: { prepare: () => ({ bind: () => ({ all: async () => ({ results: [memberRow()] }) }) }) },
    OMNIA_QUERY_STORE_ID: 'store-01-test', OMNIA_SEARCH_HMAC_SECRET_BASE64: secret,
    OMNIA_TELEGRAM_BOT_TOKEN: 'bot-token', OMNIA_TELEGRAM_WEBHOOK_SECRET: 'webhook-secret', OMNIA_TELEGRAM_ALLOWED_USER_ID: '7',
    TELEGRAM_FETCH: async (url, options) => { calls.push({ url, body: JSON.parse(options.body) }); return new Response(JSON.stringify({ ok: true }), { status: 200 }); }
  };
}

function telegramRequest(update) {
  return new Request('https://cloud.example/telegram/webhook', {
    method: 'POST', body: JSON.stringify(update), headers: { 'content-type': 'application/json', 'X-Telegram-Bot-Api-Secret-Token': 'webhook-secret' }
  });
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
