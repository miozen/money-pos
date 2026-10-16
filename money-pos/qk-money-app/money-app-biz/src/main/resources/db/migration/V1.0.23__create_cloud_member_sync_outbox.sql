CREATE TABLE `cloud_member_sync_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `member_id` bigint NOT NULL,
  `operation` varchar(16) NOT NULL,
  `snapshot_version` bigint NOT NULL DEFAULT 1,
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `retry_count` int NOT NULL DEFAULT 0,
  `next_retry_at` datetime NOT NULL DEFAULT current_timestamp(),
  `last_error` varchar(500) DEFAULT NULL,
  `sent_at` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT current_timestamp(),
  `update_time` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cloud_member_sync_outbox_member` (`tenant_id`, `member_id`),
  KEY `idx_cloud_member_sync_outbox_dispatch` (`status`, `next_retry_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Omnia Cloud会员查询同步待发箱';
