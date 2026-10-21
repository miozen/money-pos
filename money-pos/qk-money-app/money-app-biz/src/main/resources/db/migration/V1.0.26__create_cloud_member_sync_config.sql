CREATE TABLE `cloud_member_sync_config` (
  `id` bigint NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT 0,
  `endpoint` varchar(255) DEFAULT NULL,
  `store_id` varchar(64) DEFAULT NULL,
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `key_id` varchar(64) DEFAULT NULL,
  `signing_secret_base64` varchar(512) DEFAULT NULL,
  `search_hmac_secret_base64` varchar(512) DEFAULT NULL,
  `initial_backfill_enabled` tinyint(1) NOT NULL DEFAULT 0,
  `initial_backfill_batch_size` int NOT NULL DEFAULT 500,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Omnia Cloud本机同步配置';

INSERT INTO `cloud_member_sync_config` (`id`, `enabled`, `tenant_id`, `initial_backfill_enabled`, `initial_backfill_batch_size`)
VALUES (1, 0, 0, 0, 500);

UPDATE `sys_permission` SET `permission_name` = 'Omnia Cloud 配置' WHERE `id` = 2040000000000000007;
INSERT INTO `sys_permission` (id, permission_name, permission_type, parent_id, icon, permission, router_path, iframe, hidden, component_name, component_path, sub_count, sort, create_by, create_time, update_by, update_time, tenant_id)
VALUES (2040000000000000011, '管理 Omnia Cloud 配置', 'BUTTON', 2040000000000000007, '', 'omniaCloud:manage', '', 0, 0, '', '', 0, 2, 'money', NOW(), 'money', NOW(), 0);
INSERT INTO `sys_role_permission_relation` (id, permission_id, role_id, tenant_id)
SELECT 2040000000000000012 + role.id, 2040000000000000011, role.id, role.tenant_id
FROM `sys_role` role
WHERE role.role_code = 'SUPER_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_permission_relation` existing WHERE existing.role_id = role.id AND existing.permission_id = 2040000000000000011 AND existing.tenant_id = role.tenant_id);
