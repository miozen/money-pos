CREATE TABLE `sys_username_reservation` (
  `id` bigint NOT NULL,
  `username` varchar(255) NOT NULL COMMENT '改名前的登录名',
  `expires_at` datetime NOT NULL COMMENT '旧 JWT 最长有效期结束时间',
  `create_by` varchar(32) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT current_timestamp(),
  `update_by` varchar(32) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username_reservation` (`username`, `tenant_id`),
  KEY `idx_username_reservation_expire` (`tenant_id`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='改名登录名保护期';
