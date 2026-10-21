CREATE TABLE `cloud_member_sync_bootstrap` (
  `tenant_id` bigint NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `cursor_member_id` bigint NOT NULL DEFAULT 0,
  `total_enqueued` bigint NOT NULL DEFAULT 0,
  `started_at` datetime NOT NULL DEFAULT current_timestamp(),
  `completed_at` datetime DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`tenant_id`),
  KEY `idx_cloud_member_sync_bootstrap_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Omnia Cloud首次会员全量回填检查点';
