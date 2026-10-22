-- `key_id` belonged to the removed multi-client model. Keep only the partition identity used by the single-store receiver.
CREATE TABLE sync_nonces_next (
  store_id TEXT NOT NULL,
  nonce TEXT NOT NULL,
  expires_at INTEGER NOT NULL,
  PRIMARY KEY (store_id, nonce)
);
INSERT INTO sync_nonces_next (store_id, nonce, expires_at)
  SELECT key_id, nonce, expires_at FROM sync_nonces;
DROP TABLE sync_nonces;
ALTER TABLE sync_nonces_next RENAME TO sync_nonces;
CREATE INDEX idx_sync_nonces_expires_at ON sync_nonces (expires_at);

CREATE TABLE sync_audit_next (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  store_id TEXT NOT NULL,
  received_at INTEGER NOT NULL,
  item_count INTEGER NOT NULL,
  accepted_count INTEGER NOT NULL
);
INSERT INTO sync_audit_next (id, store_id, received_at, item_count, accepted_count)
  SELECT id, store_id, received_at, item_count, accepted_count FROM sync_audit;
DROP TABLE sync_audit;
ALTER TABLE sync_audit_next RENAME TO sync_audit;
CREATE INDEX idx_sync_audit_store_received ON sync_audit (store_id, received_at DESC);
