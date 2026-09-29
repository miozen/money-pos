CREATE TABLE `oms_member_amount_receipt` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `receipt_no` varchar(32) NOT NULL,
  `request_no` varchar(64) NOT NULL,
  `receipt_type` varchar(48) NOT NULL,
  `member_id` bigint NOT NULL,
  `brand_id` varchar(50) NOT NULL,
  `amount_right_id` bigint DEFAULT NULL,
  `source_pickup_no` varchar(32) DEFAULT NULL,
  `source_receipt_no` varchar(32) DEFAULT NULL,
  `total_amount` decimal(12,2) NOT NULL DEFAULT 0.00,
  `right_deduct_amount` decimal(12,2) NOT NULL DEFAULT 0.00,
  `supplement_amount` decimal(12,2) NOT NULL DEFAULT 0.00,
  `cost_amount` decimal(12,2) NOT NULL DEFAULT 0.00,
  `status` varchar(32) NOT NULL DEFAULT 'COMPLETED',
  `create_by` varchar(32) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT current_timestamp(),
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_amount_receipt_no` (`receipt_no`),
  UNIQUE KEY `uk_amount_receipt_request` (`request_no`),
  KEY `idx_amount_receipt_member_type` (`member_id`,`receipt_type`,`status`),
  KEY `idx_amount_receipt_pickup` (`source_pickup_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员金额权益非商品业务凭证';

CREATE TABLE `oms_member_amount_receipt_pay` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `receipt_no` varchar(32) NOT NULL,
  `pay_method_code` varchar(32) NOT NULL,
  `pay_method_name` varchar(64) NOT NULL DEFAULT '',
  `pay_tag` varchar(64) DEFAULT NULL,
  `pay_amount` decimal(12,2) NOT NULL,
  `original_amount` decimal(12,2) NOT NULL,
  `net_amount` decimal(12,2) NOT NULL,
  `change_allocated` decimal(12,2) NOT NULL DEFAULT 0.00,
  `create_time` datetime NOT NULL DEFAULT current_timestamp(),
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), KEY `idx_amount_receipt_pay_receipt` (`receipt_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员金额权益非商品凭证支付明细';

CREATE TABLE `oms_member_amount_pickup` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pickup_no` varchar(32) NOT NULL,
  `request_no` varchar(64) NOT NULL,
  `member_id` bigint NOT NULL,
  `amount_right_id` bigint NOT NULL,
  `supplement_receipt_no` varchar(32) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'COMPLETED',
  `refund_no` varchar(32) DEFAULT NULL,
  `create_by` varchar(32) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT current_timestamp(),
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_amount_pickup_no` (`pickup_no`),
  UNIQUE KEY `uk_amount_pickup_request` (`request_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员金额权益提货单';

CREATE TABLE `oms_member_amount_pickup_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pickup_no` varchar(32) NOT NULL,
  `goods_id` bigint NOT NULL,
  `quantity` int NOT NULL,
  `unit_price` decimal(12,2) NOT NULL,
  `line_amount` decimal(12,2) NOT NULL,
  `purchase_price` decimal(12,2) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT current_timestamp(),
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), KEY `idx_amount_pickup_item_pickup` (`pickup_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员金额权益提货明细';
