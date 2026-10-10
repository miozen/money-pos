ALTER TABLE `sys_print_config`
  ADD COLUMN `member_pickup_auto_print` tinyint(1) NOT NULL DEFAULT 1 COMMENT '会员提货单自动打印';
