ALTER TABLE `ums_member`
  ADD COLUMN `merged_to_member_id` bigint DEFAULT NULL COMMENT '合并后保留的会员ID',
  ADD COLUMN `merged_time` datetime DEFAULT NULL COMMENT '合并完成时间';
