INSERT INTO sys_permission (id, permission_name, permission_type, parent_id, icon, permission, router_path, iframe, hidden, component_name, component_path, sub_count, sort, create_by, create_time, update_by, update_time, tenant_id)
VALUES (2040000000000000005, '权益档位管理', 'MENU', 1629388418109894657, 'Medal', '', 'ums/member-benefit-tier', 0, 0, 'MemberBenefitTier', 'ums/memberBenefitTier/index', 1, 6, 'money', NOW(), 'money', NOW(), 0);
UPDATE sys_permission SET parent_id = 2040000000000000005, sort = 1 WHERE id = 2040000000000000004;
INSERT INTO sys_role_permission_relation (id, permission_id, role_id, tenant_id)
SELECT 5000000000000000000 + relation.role_id, 2040000000000000005, relation.role_id, relation.tenant_id
FROM sys_role_permission_relation relation
WHERE relation.permission_id = 2040000000000000004
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission_relation existing WHERE existing.role_id = relation.role_id AND existing.permission_id = 2040000000000000005 AND existing.tenant_id = relation.tenant_id);
