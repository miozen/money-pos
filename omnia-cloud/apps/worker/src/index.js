const MAX_CLOCK_SKEW_MS = 5 * 60 * 1000;
const MAX_BATCH_SIZE = 100;
const MAX_SQLITE_INTEGER = 9223372036854775807n;

const json = (body, status = 200, extraHeaders = {}) => new Response(JSON.stringify(body), {
  status, headers: { 'content-type': 'application/json; charset=utf-8', ...extraHeaders }
});

const queryHeaders = { 'cache-control': 'no-store' };

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
  if (!snapshot || !isPositiveSqliteInteger(snapshot.memberId) || !isPositiveSqliteInteger(snapshot.version) ||
      !['UPSERT', 'TOMBSTONE'].includes(snapshot.operation) ||
      !Number.isSafeInteger(snapshot.sourceUpdatedAt)) return 'snapshot fields are invalid';
  if (containsRawPhone(snapshot)) return 'raw phone must not be uploaded';
  if (snapshot.operation === 'UPSERT' && (!snapshot.snapshot || typeof snapshot.snapshot !== 'object')) return 'upsert snapshot is required';
  return null;
}

function isPositiveSqliteInteger(value) {
  const text = typeof value === 'string' ? value : Number.isSafeInteger(value) ? String(value) : null;
  return text !== null && /^[1-9][0-9]*$/.test(text) && BigInt(text) <= MAX_SQLITE_INTEGER;
}

function sqliteIntegerText(value) { return typeof value === 'string' ? value : String(value); }

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
    .bind(storeId, sqliteIntegerText(snapshot.memberId), sqliteIntegerText(snapshot.version), isUpsert ? 'ACTIVE' : 'DELETED',
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

function queryUnavailable() { return json({ error: 'query service is not configured' }, 503, queryHeaders); }

function queryUnauthorized() {
  return json({ error: 'query authorization required' }, 401, {
    ...queryHeaders, 'www-authenticate': 'Basic realm="Omnia Cloud"'
  });
}

function hasQueryPassword(request, env) {
  if (typeof env.OMNIA_QUERY_PASSWORD !== 'string' || !env.OMNIA_QUERY_PASSWORD) return null;
  const authorization = request.headers.get('authorization') || '';
  if (!authorization.startsWith('Basic ')) return false;
  try {
    const credentials = atob(authorization.slice(6));
    const separator = credentials.indexOf(':');
    return separator >= 0 && constantTimeEqual(credentials.slice(separator + 1), env.OMNIA_QUERY_PASSWORD);
  } catch {
    return false;
  }
}

function queryConfiguration(env) {
  if (typeof env.OMNIA_QUERY_STORE_ID !== 'string' || !env.OMNIA_QUERY_STORE_ID ||
      typeof env.OMNIA_SEARCH_HMAC_SECRET_BASE64 !== 'string' || !env.OMNIA_SEARCH_HMAC_SECRET_BASE64) return null;
  return { storeId: env.OMNIA_QUERY_STORE_ID, searchSecret: env.OMNIA_SEARCH_HMAC_SECRET_BASE64 };
}

function normalizePhone(value) { return value.replace(/\D/g, ''); }
function normalizeName(value) { return value.trim().toLowerCase(); }
function likePrefix(value) { return `${value.replace(/[\\%_]/g, '\\$&')}%`; }

async function queryCriteria(query, searchSecret) {
  if (typeof query !== 'string' || !query.trim() || query.length > 80) return { error: 'query is invalid' };
  const digits = normalizePhone(query);
  if (digits.length === 11) return { column: 'phone_hmac', value: await hmacHex(searchSecret, digits) };
  if (digits.length === 4 && query.trim().replace(/\s/g, '') === digits) return { column: 'phone_last4_hmac', value: await hmacHex(searchSecret, digits) };
  return { column: 'name_normalized', value: likePrefix(normalizeName(query)) };
}

function displayProjection(row) {
  let snapshot = {};
  try { snapshot = JSON.parse(row.snapshot_json || '{}'); } catch { /* invalid historical projection is displayed as empty assets */ }
  return {
    memberId: String(row.member_id), name: row.name, phoneMasked: row.phone_masked,
    memberCode: snapshot.memberCode ?? null, memberType: snapshot.memberType ?? null,
    balance: snapshot.balance ?? '0', couponBalance: snapshot.couponBalance ?? '0',
    brandLevels: snapshot.brandLevels ?? {}, unusedCoupons: snapshot.unusedCoupons ?? [],
    quantityRights: snapshot.quantityRights ?? [], amountRights: snapshot.amountRights ?? [],
    targetPlans: snapshot.targetPlans ?? [], sourceUpdatedAt: row.source_updated_at,
    syncedAt: row.synced_at
  };
}

async function membersByCriteria(db, storeId, criteria) {
  const sql = criteria.column === 'name_normalized'
    ? "SELECT CAST(member_id AS TEXT) AS member_id, name, phone_masked, snapshot_json, source_updated_at, synced_at FROM member_snapshots WHERE store_id = ? AND status = 'ACTIVE' AND name_normalized LIKE ? ESCAPE '\\' ORDER BY synced_at DESC LIMIT 20"
    : `SELECT CAST(member_id AS TEXT) AS member_id, name, phone_masked, snapshot_json, source_updated_at, synced_at FROM member_snapshots WHERE store_id = ? AND status = 'ACTIVE' AND ${criteria.column} = ? ORDER BY synced_at DESC LIMIT 20`;
  const result = await db.prepare(sql).bind(storeId, criteria.value).all();
  return (result.results || []).map(displayProjection);
}

export async function handleMemberSearch(request, env) {
  const authorized = hasQueryPassword(request, env);
  if (authorized === null) return queryUnavailable();
  if (!authorized) return queryUnauthorized();
  const configuration = queryConfiguration(env);
  if (!configuration) return queryUnavailable();
  let body;
  try { body = await request.json(); } catch { return json({ error: 'invalid JSON' }, 400, queryHeaders); }
  let criteria;
  try { criteria = await queryCriteria(body?.query, configuration.searchSecret); } catch { return queryUnavailable(); }
  if (criteria.error) return json({ error: criteria.error }, 400, queryHeaders);
  try {
    return json({ members: await membersByCriteria(env.DB, configuration.storeId, criteria) }, 200, queryHeaders);
  } catch {
    return json({ error: 'query failed' }, 503, queryHeaders);
  }
}

export async function handleMemberDetail(request, env, memberId) {
  const authorized = hasQueryPassword(request, env);
  if (authorized === null) return queryUnavailable();
  if (!authorized) return queryUnauthorized();
  const configuration = queryConfiguration(env);
  if (!configuration) return queryUnavailable();
  if (!isPositiveSqliteInteger(memberId)) return json({ error: 'member id is invalid' }, 400, queryHeaders);
  try {
    const row = await env.DB.prepare("SELECT CAST(member_id AS TEXT) AS member_id, name, phone_masked, snapshot_json, source_updated_at, synced_at FROM member_snapshots WHERE store_id = ? AND member_id = ? AND status = 'ACTIVE'")
      .bind(configuration.storeId, memberId).first();
    return row ? json({ member: displayProjection(row) }, 200, queryHeaders) : json({ error: 'member not found' }, 404, queryHeaders);
  } catch {
    return json({ error: 'query failed' }, 503, queryHeaders);
  }
}

const queryPage = `<!doctype html><html lang="zh-CN"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Omnia Cloud</title><style>
*{box-sizing:border-box}body{margin:0;background:#f5f6f8;color:#172033;font:16px -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}.app{max-width:680px;margin:auto;padding:24px 16px 48px}h1{font-size:24px;margin:0 0 6px}.hint{color:#667085;font-size:13px;margin:0 0 20px}.panel,.member{background:#fff;border:1px solid #e5e7eb;border-radius:12px;padding:14px;margin:10px 0;box-shadow:0 1px 2px #1018280a}label{display:block;font-weight:600;font-size:14px;margin:12px 0 6px}input,button{font:inherit;border-radius:9px;padding:11px;width:100%}input{border:1px solid #cbd5e1;background:#fff}button{margin-top:12px;border:0;background:#2563eb;color:white;font-weight:650}button:disabled{background:#94a3b8}.member{cursor:pointer}.member strong{display:block}.member span,.status{display:block;color:#667085;font-size:13px;margin-top:4px}.error{color:#b42318}.details{white-space:pre-wrap;line-height:1.6;font-size:14px}.hidden{display:none}</style><main class="app"><h1>Omnia Cloud</h1><p class="hint">会员查询快照，仅供本人查询；数据可能存在短暂同步延迟。</p><section class="panel"><label for="password">查询密码</label><input id="password" type="password" autocomplete="current-password"><label for="query">姓名、完整手机号或后四位</label><input id="query" autocomplete="off" placeholder="例如：张三、13800138000、8000"><button id="search">查询会员</button><span id="status" class="status"></span></section><section id="results"></section></main><script>
const password=document.querySelector('#password'),query=document.querySelector('#query'),button=document.querySelector('#search'),status=document.querySelector('#status'),results=document.querySelector('#results');
function auth(){return 'Basic '+btoa('omnia:'+password.value)} function line(label,value){const p=document.createElement('p');p.textContent=label+'：'+(value??'—');return p}
async function api(path,options={}){const response=await fetch(path,{...options,headers:{...options.headers,Authorization:auth()}});const body=await response.json();if(!response.ok)throw new Error(body.error||'查询失败');return body}
function values(items,mapper){return items&&items.length?items.map(mapper).join('；'):'无'} function levels(value){const rows=Object.entries(value||{}).map(([brand,level])=>brand+'：'+level);return rows.length?rows.join('；'):'无'}
function showMember(member,detail){const card=document.createElement('article');card.className='member';card.append(document.createElement('strong'));card.firstChild.textContent=member.name||'未命名会员';card.append(line('手机号',member.phoneMasked),line('编号',member.memberCode));if(detail){const d=document.createElement('div');d.className='details';d.textContent='余额：'+member.balance+'\\n优惠余额：'+member.couponBalance+'\\n品牌等级：'+levels(member.brandLevels)+'\\n可用优惠券：'+values(member.unusedCoupons,x=>(x.ruleName||x.ruleId||'优惠券')+' ×'+(x.count??0))+'\\n次数权益：'+values(member.quantityRights,x=>(x.goodsName||x.goodsId||'权益')+'，剩余 '+(x.remainingQuantity??0)+' 次')+'\\n金额权益：'+values(member.amountRights,x=>(x.tierName||x.tierCode||'权益')+'，剩余 '+(x.remainingAmount??0))+'\\n目标权益：'+values(member.targetPlans,x=>(x.targetTierName||x.targetTierCode||'目标')+'，进度 '+(x.progressAmount??0)+' / '+(x.targetAmount??0)+'，剩余 '+(x.remainingAmount??0))+'\\n最后同步：'+new Date(member.syncedAt).toLocaleString();card.append(d)}else{card.onclick=async()=>{try{status.textContent='正在读取详情…';const data=await api('/api/v1/members/'+member.memberId);results.replaceChildren();showMember(data.member,true);status.textContent=''}catch(e){status.className='status error';status.textContent=e.message}}}results.append(card)}
button.onclick=async()=>{results.replaceChildren();status.className='status';if(!password.value||!query.value.trim()){status.className='status error';status.textContent='请输入查询密码和查询内容';return}button.disabled=true;try{const data=await api('/api/v1/members/search',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({query:query.value})});if(!data.members.length)status.textContent='没有匹配的有效会员';else{status.textContent='找到 '+data.members.length+' 位会员，点击查看详情';data.members.forEach(m=>showMember(m,false))}}catch(e){status.className='status error';status.textContent=e.message}finally{button.disabled=false}};
</script></html>`;

function page() { return new Response(queryPage, { headers: { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' } }); }

export default {
  fetch(request, env) {
    const pathname = new URL(request.url).pathname;
    if (request.method === 'POST' && pathname === '/internal/v1/member-snapshots:batch') return handleSync(request, env);
    if (request.method === 'POST' && pathname === '/internal/v1/member-snapshots:test') return handleConnectionTest(request, env);
    if (request.method === 'GET' && pathname === '/') return page();
    if (request.method === 'POST' && pathname === '/api/v1/members/search') return handleMemberSearch(request, env);
    const detail = pathname.match(/^\/api\/v1\/members\/([0-9]+)$/);
    if (request.method === 'GET' && detail) return handleMemberDetail(request, env, detail[1]);
    return json({ error: 'not found' }, 404);
  }
};
