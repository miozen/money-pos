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
  const keyId = request.headers.get('X-Omnia-Key-Id');
  const timestamp = Number(request.headers.get('X-Omnia-Timestamp'));
  const nonce = request.headers.get('X-Omnia-Nonce');
  const bodyHash = request.headers.get('X-Omnia-Body-SHA256');
  const signature = request.headers.get('X-Omnia-Signature');
  if (!keyId || !nonce || !bodyHash || !signature || !Number.isSafeInteger(timestamp) || Math.abs(now - timestamp) > MAX_CLOCK_SKEW_MS) {
    return { error: 'invalid signing headers', status: 401 };
  }
  let keys;
  try { keys = JSON.parse(env.OMNIA_SYNC_KEYS_JSON); } catch { return { error: 'receiver key configuration is invalid', status: 500 }; }
  const secret = keys[keyId];
  if (typeof secret !== 'string') return { error: 'unknown sync key', status: 401 };
  const body = await request.text();
  if (!constantTimeEqual(await sha256Hex(body), bodyHash)) return { error: 'body hash mismatch', status: 401 };
  const canonical = `${request.method}\n${new URL(request.url).pathname}\n${timestamp}\n${nonce}\n${bodyHash}`;
  if (!constantTimeEqual(await hmacHex(secret, canonical), signature)) return { error: 'signature mismatch', status: 401 };
  return { keyId, nonce, body };
}

async function claimNonceAndClient(db, keyId, nonce, now) {
  const client = await db.prepare('SELECT store_id, active FROM sync_clients WHERE key_id = ?').bind(keyId).first();
  if (!client || client.active !== 1) return { error: 'sync client is not active', status: 403 };
  try {
    await db.prepare('INSERT INTO sync_nonces (key_id, nonce, expires_at) VALUES (?, ?, ?)').bind(keyId, nonce, now + MAX_CLOCK_SKEW_MS).run();
  } catch {
    return { error: 'replayed request', status: 409 };
  }
  return { storeId: client.store_id };
}

async function persistSnapshot(db, storeId, snapshot, now) {
  const isUpsert = snapshot.operation === 'UPSERT';
  const data = isUpsert ? snapshot.snapshot : {};
  await db.prepare(`INSERT INTO member_snapshots (
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
      data.phoneMasked ?? null, isUpsert ? JSON.stringify(data) : null, snapshot.sourceUpdatedAt, now).run();
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
  const client = await claimNonceAndClient(env.DB, verified.keyId, verified.nonce, now);
  if (client.error) return json({ error: client.error }, client.status);
  if (client.storeId !== payload.storeId) return json({ error: 'store does not match sync key' }, 403);
  for (const snapshot of payload.snapshots) await persistSnapshot(env.DB, payload.storeId, snapshot, now);
  await env.DB.prepare('INSERT INTO sync_audit (store_id, key_id, received_at, item_count, accepted_count) VALUES (?, ?, ?, ?, ?)')
    .bind(payload.storeId, verified.keyId, now, payload.snapshots.length, payload.snapshots.length).run();
  return json({ accepted: payload.snapshots.length });
}

export default {
  fetch(request, env) {
    if (request.method === 'POST' && new URL(request.url).pathname === '/internal/v1/member-snapshots:batch') return handleSync(request, env);
    return json({ error: 'not found' }, 404);
  }
};
