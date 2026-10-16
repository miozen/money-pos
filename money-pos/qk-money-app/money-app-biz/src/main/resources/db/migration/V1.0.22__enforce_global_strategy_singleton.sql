-- SYS-OPT-4: protect the existing one-row global strategy without rewriting any values.
-- A pre-existing duplicate is intentionally a migration error requiring manual business review.
ALTER TABLE `sys_strategy`
    ADD COLUMN `singleton_key` tinyint NOT NULL DEFAULT 1 COMMENT '全局策略单例固定键' AFTER `id`,
    ADD UNIQUE KEY `uk_sys_strategy_singleton` (`singleton_key`);
