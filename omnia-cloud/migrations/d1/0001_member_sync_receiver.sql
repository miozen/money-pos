-- Omnia Cloud D1 migration 0001. Apply with the independently configured Worker project only.

CREATE TABLE IF NOT EXISTS sync_clients (
  store_id TEXT PRIMARY KEY,
  key_id TEXT NOT NULL UNIQUE,
  active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS sync_nonces (
  key_id TEXT NOT NULL,
  nonce TEXT NOT NULL,
  expires_at INTEGER NOT NULL,
  PRIMARY KEY (key_id, nonce)
);
CREATE INDEX IF NOT EXISTS idx_sync_nonces_expires_at ON sync_nonces (expires_at);

CREATE TABLE IF NOT EXISTS member_snapshots (
  store_id TEXT NOT NULL,
  member_id INTEGER NOT NULL,
  snapshot_version INTEGER NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'DELETED')),
  name TEXT,
  name_normalized TEXT,
  phone_hmac TEXT,
  phone_last4_hmac TEXT,
  phone_masked TEXT,
  snapshot_json TEXT,
  source_updated_at INTEGER,
  synced_at INTEGER NOT NULL,
  PRIMARY KEY (store_id, member_id),
  FOREIGN KEY (store_id) REFERENCES sync_clients(store_id)
);
CREATE INDEX IF NOT EXISTS idx_member_snapshots_name ON member_snapshots (store_id, name_normalized);
CREATE INDEX IF NOT EXISTS idx_member_snapshots_phone ON member_snapshots (store_id, phone_hmac);
CREATE INDEX IF NOT EXISTS idx_member_snapshots_last4 ON member_snapshots (store_id, phone_last4_hmac);

CREATE TABLE IF NOT EXISTS sync_audit (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  store_id TEXT NOT NULL,
  key_id TEXT NOT NULL,
  received_at INTEGER NOT NULL,
  item_count INTEGER NOT NULL,
  accepted_count INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_sync_audit_store_received ON sync_audit (store_id, received_at DESC);
