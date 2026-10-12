ALTER TABLE `trade_business_document_sequence`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 0 AFTER `sequence_value`,
  DROP INDEX `uk_trade_document_sequence`,
  ADD UNIQUE KEY `uk_trade_document_sequence` (`tenant_id`, `document_prefix`, `business_date`);
