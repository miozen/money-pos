CREATE TABLE ums_brand_amount_package (
 id bigint NOT NULL AUTO_INCREMENT, brand_id varchar(50) NOT NULL, package_code varchar(50) NOT NULL, package_name varchar(100) NOT NULL,
 purchase_amount decimal(12,2) NOT NULL, benefit_amount decimal(12,2) NOT NULL, pricing_level_code varchar(50) NOT NULL,
 enabled tinyint(1) NOT NULL DEFAULT 1, sort_no int NOT NULL DEFAULT 0, remark varchar(255) NOT NULL DEFAULT '', legacy_tier_code varchar(50) DEFAULT NULL,
 create_by varchar(32) NOT NULL DEFAULT '', create_time datetime NOT NULL DEFAULT current_timestamp(), update_by varchar(32) NOT NULL DEFAULT '', update_time datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(), tenant_id bigint NOT NULL DEFAULT 0,
 PRIMARY KEY(id), UNIQUE KEY uk_amount_package_code(brand_id,package_code), KEY idx_amount_package_available(brand_id,enabled,sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品牌金额权益包';
INSERT INTO ums_brand_amount_package (brand_id,package_code,package_name,purchase_amount,benefit_amount,pricing_level_code,enabled,sort_no,remark,legacy_tier_code,tenant_id)
SELECT brand_id,tier_code,tier_name,configured_amount,configured_amount,pricing_level_code,enabled,sort_no,remark,tier_code,tenant_id FROM ums_brand_benefit_tier;
INSERT INTO sys_permission (id, permission_name, permission_type, parent_id, icon, permission, router_path, iframe, hidden, component_name, component_path, sub_count, sort, create_by, create_time, update_by, update_time, tenant_id)
VALUES (2040000000000000006, '金额权益包管理', 'MENU', 1629388418109894657, 'Present', '', 'ums/member-amount-package', 0, 0, 'MemberAmountPackage', 'ums/memberAmountPackage/index', 0, 7, 'money', NOW(), 'money', NOW(), 0);
INSERT INTO sys_role_permission_relation (id, permission_id, role_id, tenant_id)
SELECT 6000000000000000000 + relation.role_id, 2040000000000000006, relation.role_id, relation.tenant_id FROM sys_role_permission_relation relation WHERE relation.permission_id=2040000000000000004 AND NOT EXISTS (SELECT 1 FROM sys_role_permission_relation existing WHERE existing.role_id=relation.role_id AND existing.permission_id=2040000000000000006 AND existing.tenant_id=relation.tenant_id);
