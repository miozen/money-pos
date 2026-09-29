CREATE TABLE `oms_member_quantity_pickup` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pickup_no` varchar(32) NOT NULL,
  `request_no` varchar(64) NOT NULL,
  `member_id` bigint NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'COMPLETED',
  `create_by` varchar(32) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT current_timestamp(),
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_pickup_no` (`pickup_no`), UNIQUE KEY `uk_pickup_request` (`request_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员数量权益提货单';

CREATE TABLE `oms_member_quantity_pickup_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pickup_no` varchar(32) NOT NULL,
  `quantity_right_id` bigint NOT NULL,
  `goods_id` bigint NOT NULL,
  `quantity` int NOT NULL,
  `create_time` datetime NOT NULL DEFAULT current_timestamp(),
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), KEY `idx_pickup_item_pickup` (`pickup_no`), KEY `idx_pickup_item_right` (`quantity_right_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员数量权益提货明细';
