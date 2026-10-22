-- Retain the historical sync_clients table, but remove its foreign-key dependency so single-store partitioned uploads do not require D1 client setup.
PRAGMA foreign_keys = OFF;
CREATE TABLE member_snapshots_next (
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
  PRIMARY KEY (store_id, member_id)
);
INSERT INTO member_snapshots_next SELECT store_id, member_id, snapshot_version, status, name, name_normalized, phone_hmac, phone_last4_hmac, phone_masked, snapshot_json, source_updated_at, synced_at FROM member_snapshots;
DROP TABLE member_snapshots;
ALTER TABLE member_snapshots_next RENAME TO member_snapshots;
CREATE INDEX idx_member_snapshots_name ON member_snapshots (store_id, name_normalized);
CREATE INDEX idx_member_snapshots_phone ON member_snapshots (store_id, phone_hmac);
CREATE INDEX idx_member_snapshots_last4 ON member_snapshots (store_id, phone_last4_hmac);
PRAGMA foreign_keys = ON;
