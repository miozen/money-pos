ALTER TABLE `ums_member_target_plan`
  ADD COLUMN `cancelled_by` varchar(64) DEFAULT NULL AFTER `confirmed_time`,
  ADD COLUMN `cancelled_time` datetime DEFAULT NULL AFTER `cancelled_by`,
  ADD COLUMN `cancel_request_no` varchar(64) DEFAULT NULL AFTER `cancelled_time`,
  ADD COLUMN `cancel_reason` varchar(255) DEFAULT NULL AFTER `cancel_request_no`,
  ADD UNIQUE KEY `uk_target_plan_cancel_request` (`cancel_request_no`);
