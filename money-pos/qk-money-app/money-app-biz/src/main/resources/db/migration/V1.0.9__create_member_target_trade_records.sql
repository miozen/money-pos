CREATE TABLE `oms_member_target_sale_contribution` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `target_plan_id` bigint NOT NULL,
  `order_no` varchar(64) NOT NULL,
  `member_id` bigint NOT NULL,
  `brand_id` varchar(50) NOT NULL,
  `contribution_amount` decimal(12,2) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'COMPLETED',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_target_plan_order` (`target_plan_id`,`order_no`),
  KEY `idx_target_sale_order` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='TARGET即时销售进度关联';

CREATE TABLE `oms_member_target_receipt` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `receipt_no` varchar(64) NOT NULL,
  `request_no` varchar(64) NOT NULL,
  `target_plan_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `receipt_type` varchar(32) NOT NULL,
  `amount` decimal(12,2) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'COMPLETED',
  `reason` varchar(255) NOT NULL DEFAULT '',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_target_receipt_no` (`receipt_no`),
  UNIQUE KEY `uk_target_receipt_request` (`request_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='TARGET补差或豁免非商品凭证';

CREATE TABLE `oms_member_target_receipt_pay` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `receipt_no` varchar(64) NOT NULL,
  `pay_method_code` varchar(32) NOT NULL,
  `pay_method_name` varchar(64) NOT NULL DEFAULT '',
  `pay_tag` varchar(128) DEFAULT NULL,
  `pay_amount` decimal(12,2) NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_target_receipt_pay` (`receipt_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='TARGET非商品凭证支付明细';
