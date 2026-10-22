const MAX_CLOCK_SKEW_MS = 5 * 60 * 1000;
const MAX_BATCH_SIZE = 100;

const json = (body, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { 'content-type': 'application/json; charset=utf-8' }
});

const hex = (bytes) => Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
const utf8 = new TextEncoder();

export async function sha256Hex(value) {
  return hex(new Uint8Array(await crypto.subtle.digest('SHA-256', utf8.encode(value))));
}

function base64Bytes(value) {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
  const raw = atob(normalized + '='.repeat((4 - normalized.length % 4) % 4));
  return Uint8Array.from(raw, (char) => char.charCodeAt(0));
}

export async function hmacHex(secretBase64, value) {
  const key = await crypto.subtle.importKey('raw', base64Bytes(secretBase64), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  return hex(new Uint8Array(await crypto.subtle.sign('HMAC', key, utf8.encode(value))));
}

function constantTimeEqual(left, right) {
  if (left.length !== right.length) return false;
  let result = 0;
  for (let index = 0; index < left.length; index += 1) result |= left.charCodeAt(index) ^ right.charCodeAt(index);
  return result === 0;
}

export function validateSnapshot(snapshot) {
  if (!snapshot || !Number.isSafeInteger(snapshot.memberId) || snapshot.memberId <= 0 ||
      !Number.isSafeInteger(snapshot.version) || snapshot.version <= 0 ||
      !['UPSERT', 'TOMBSTONE'].includes(snapshot.operation) ||
      !Number.isSafeInteger(snapshot.sourceUpdatedAt)) return 'snapshot fields are invalid';
  if (containsRawPhone(snapshot)) return 'raw phone must not be uploaded';
  if (snapshot.operation === 'UPSERT' && (!snapshot.snapshot || typeof snapshot.snapshot !== 'object')) return 'upsert snapshot is required';
  return null;
}

function containsRawPhone(value) {
  if (!value || typeof value !== 'object') return false;
  if (Array.isArray(value)) return value.some(containsRawPhone);
  return Object.entries(value).some(([key, nested]) => key === 'phone' || containsRawPhone(nested));
}

export async function verifySignedRequest(request, env, now = Date.now()) {
  const timestamp = Number(request.headers.get('X-Omnia-Timestamp'));
  const nonce = request.headers.get('X-Omnia-Nonce');
  const bodyHash = request.headers.get('X-Omnia-Body-SHA256');
  const signature = request.headers.get('X-Omnia-Signature');
  if (!nonce || !bodyHash || !signature || !Number.isSafeInteger(timestamp) || Math.abs(now - timestamp) > MAX_CLOCK_SKEW_MS) {
    return { error: 'invalid signing headers', status: 401 };
  }
  const body = await request.text();
  if (!constantTimeEqual(await sha256Hex(body), bodyHash)) return { error: 'body hash mismatch', status: 401 };
  let payload;
  let stores;
  try { payload = JSON.parse(body); stores = JSON.parse(env.OMNIA_SYNC_STORE_SECRETS_JSON); } catch { return { error: 'receiver configuration or body is invalid', status: 400 }; }
  const storeId = payload?.storeId;
  const secrets = stores[storeId];
  const candidates = Array.isArray(secrets) ? secrets : [secrets];
  if (typeof storeId !== 'string' || !candidates.some(secret => typeof secret === 'string')) return { error: 'unknown sync store', status: 401 };
  const canonical = `${request.method}\n${new URL(request.url).pathname}\n${timestamp}\n${nonce}\n${bodyHash}`;
  for (const secret of candidates) if (typeof secret === 'string' && constantTimeEqual(await hmacHex(secret, canonical), signature)) return { storeId, nonce, body };
  return { error: 'signature mismatch', status: 401 };
}

async function claimNonce(db, storeId, nonce, now) {
  try {
    await db.prepare('INSERT INTO sync_nonces (store_id, nonce, expires_at) VALUES (?, ?, ?)').bind(storeId, nonce, now + MAX_CLOCK_SKEW_MS).run();
  } catch {
    return { error: 'replayed request', status: 409 };
  }
  return {};
}

function persistSnapshotStatement(db, storeId, snapshot, now) {
  const isUpsert = snapshot.operation === 'UPSERT';
  const data = isUpsert ? snapshot.snapshot : {};
  return db.prepare(`INSERT INTO member_snapshots (
      store_id, member_id, snapshot_version, status, name, name_normalized, phone_hmac, phone_last4_hmac, phone_masked, snapshot_json, source_updated_at, synced_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(store_id, member_id) DO UPDATE SET
      snapshot_version = excluded.snapshot_version, status = excluded.status, name = excluded.name,
      name_normalized = excluded.name_normalized, phone_hmac = excluded.phone_hmac,
      phone_last4_hmac = excluded.phone_last4_hmac, phone_masked = excluded.phone_masked,
      snapshot_json = excluded.snapshot_json, source_updated_at = excluded.source_updated_at, synced_at = excluded.synced_at
    WHERE excluded.snapshot_version >= member_snapshots.snapshot_version`)
    .bind(storeId, snapshot.memberId, snapshot.version, isUpsert ? 'ACTIVE' : 'DELETED',
      data.name ?? null, data.nameNormalized ?? null, data.phoneHmac ?? null, data.phoneLast4Hmac ?? null,
      data.phoneMasked ?? null, isUpsert ? JSON.stringify(data) : null, snapshot.sourceUpdatedAt, now);
}

export async function handleSync(request, env, now = Date.now()) {
  const verified = await verifySignedRequest(request, env, now);
  if (verified.error) return json({ error: verified.error }, verified.status);
  let payload;
  try { payload = JSON.parse(verified.body); } catch { return json({ error: 'invalid JSON' }, 400); }
  if (!payload || typeof payload.storeId !== 'string' || !Array.isArray(payload.snapshots) || payload.snapshots.length === 0 || payload.snapshots.length > MAX_BATCH_SIZE) {
    return json({ error: 'invalid batch' }, 400);
  }
  const invalid = payload.snapshots.map(validateSnapshot).find(Boolean);
  if (invalid) return json({ error: invalid }, 400);
  const nonceClaim = await claimNonce(env.DB, verified.storeId, verified.nonce, now);
  if (nonceClaim.error) return json({ error: nonceClaim.error }, nonceClaim.status);
  const statements = payload.snapshots.map(snapshot => persistSnapshotStatement(env.DB, payload.storeId, snapshot, now));
  statements.push(env.DB.prepare('INSERT INTO sync_audit (store_id, received_at, item_count, accepted_count) VALUES (?, ?, ?, ?)')
    .bind(payload.storeId, now, payload.snapshots.length, payload.snapshots.length));
  try {
    await env.DB.batch(statements);
  } catch {
    return json({ error: 'snapshot persistence failed' }, 503);
  }
  return json({ accepted: payload.snapshots.length });
}

export async function handleConnectionTest(request, env, now = Date.now()) {
  const verified = await verifySignedRequest(request, env, now);
  if (verified.error) return json({ error: verified.error }, verified.status);
  let payload;
  try { payload = JSON.parse(verified.body); } catch { return json({ error: 'invalid JSON' }, 400); }
  if (!payload || payload.storeId !== verified.storeId) return json({ error: 'invalid connection test' }, 400);
  try {
    await env.DB.prepare('SELECT 1').first();
  } catch {
    return json({ error: 'database unavailable' }, 503);
  }
  return json({ ok: true });
}

export default {
  fetch(request, env) {
    if (request.method === 'POST' && new URL(request.url).pathname === '/internal/v1/member-snapshots:batch') return handleSync(request, env);
    if (request.method === 'POST' && new URL(request.url).pathname === '/internal/v1/member-snapshots:test') return handleConnectionTest(request, env);
    return json({ error: 'not found' }, 404);
  }
};
