INSERT INTO sys_permission (id, permission_name, permission_type, parent_id, icon, permission, router_path, iframe, hidden, component_name, component_path, sub_count, sort, create_by, create_time, update_by, update_time, tenant_id)
VALUES
  (2040000000000000007, 'Omnia Cloud 同步', 'MENU', 1501921151197130754, 'Cloudy', '', 'system/omnia-cloud', 0, 0, 'OmniaCloudSync', 'system/omniaCloud/index', 1, 10, 'money', NOW(), 'money', NOW(), 0),
  (2040000000000000008, '查看 Omnia Cloud 同步状态', 'BUTTON', 2040000000000000007, '', 'omniaCloud:view', '', 0, 0, '', '', 0, 1, 'money', NOW(), 'money', NOW(), 0);

INSERT INTO sys_role_permission_relation (id, permission_id, role_id, tenant_id)
SELECT 2040000000000000009 + role.id, 2040000000000000007, role.id, role.tenant_id
FROM sys_role role
WHERE role.role_code = 'SUPER_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission_relation existing WHERE existing.role_id = role.id AND existing.permission_id = 2040000000000000007 AND existing.tenant_id = role.tenant_id);

INSERT INTO sys_role_permission_relation (id, permission_id, role_id, tenant_id)
SELECT 2040000000000000010 + role.id, 2040000000000000008, role.id, role.tenant_id
FROM sys_role role
WHERE role.role_code = 'SUPER_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission_relation existing WHERE existing.role_id = role.id AND existing.permission_id = 2040000000000000008 AND existing.tenant_id = role.tenant_id);
