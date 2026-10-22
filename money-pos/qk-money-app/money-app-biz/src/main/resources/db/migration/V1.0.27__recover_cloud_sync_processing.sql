ALTER TABLE `cloud_member_sync_outbox` ADD COLUMN `processing_at` datetime DEFAULT NULL AFTER `next_retry_at`;
CREATE INDEX `idx_cloud_member_sync_outbox_processing` ON `cloud_member_sync_outbox` (`status`, `processing_at`);
