CREATE TABLE `trade_business_document_sequence` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `document_prefix` varchar(12) NOT NULL,
  `business_date` char(8) NOT NULL,
  `sequence_value` int NOT NULL,
  `create_time` datetime NOT NULL DEFAULT current_timestamp(),
  `update_time` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trade_document_sequence` (`document_prefix`, `business_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易业务单据日序列';

ALTER TABLE `oms_order`
  ADD COLUMN `request_no` varchar(64) NULL COMMENT '客户端幂等请求号' AFTER `order_no`,
  ADD UNIQUE KEY `uk_order_request_no` (`request_no`);
