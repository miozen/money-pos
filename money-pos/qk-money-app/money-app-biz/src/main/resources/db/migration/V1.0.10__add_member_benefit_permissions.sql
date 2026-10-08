-- ME-1.5C: capability resources and the dynamic backend entry. No role name is assumed.
INSERT INTO sys_permission (id, permission_name, permission_type, parent_id, icon, permission, router_path, iframe, hidden, component_name, component_path, sub_count, sort, create_by, create_time, update_by, update_time, tenant_id)
VALUES
  (2040000000000000001, '会员权益', 'MENU', 1629388418109894657, 'Present', '', 'ums/member-benefit', 0, 0, 'MemberBenefit', 'ums/memberBenefit/index', 2, 5, 'money', NOW(), 'money', NOW(), 0),
  (2040000000000000002, '权益日常办理', 'BUTTON', 2040000000000000001, '', 'memberBenefit:operate', '', 0, 0, '', '', 0, 1, 'money', NOW(), 'money', NOW(), 0),
  (2040000000000000003, '权益异常管理', 'BUTTON', 2040000000000000001, '', 'memberBenefit:manage', '', 0, 0, '', '', 0, 2, 'money', NOW(), 'money', NOW(), 0),
  (2040000000000000004, '权益档位管理', 'BUTTON', 1629388418109894657, '', 'memberBenefit:tier', '', 0, 0, '', '', 0, 6, 'money', NOW(), 'money', NOW(), 0);

-- Existing POS-capable roles retain daily benefit access after the capability split; higher-risk management and tier
-- permissions remain explicitly assignable through the existing role-permission UI.
INSERT INTO sys_role_permission_relation (id, permission_id, role_id, tenant_id)
SELECT 4000000000000000000 + relation.role_id + permission.id - 2040000000000000000, permission.id, relation.tenant_id
FROM sys_role_permission_relation relation
JOIN sys_permission cashier ON cashier.id = relation.permission_id AND cashier.permission = 'pos:cashier'
JOIN sys_permission permission ON permission.id IN (2040000000000000001, 2040000000000000002)
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_permission_relation existing
  WHERE existing.role_id = relation.role_id AND existing.permission_id = permission.id AND existing.tenant_id = relation.tenant_id
);
