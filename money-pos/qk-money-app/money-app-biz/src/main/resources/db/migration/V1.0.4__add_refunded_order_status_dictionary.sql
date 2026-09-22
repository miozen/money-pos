-- Keep legacy RETURN orders readable while making REFUNDED the canonical status
-- written by the current full-refund workflow.

UPDATE `sys_dict_detail`
SET `cn_desc` = '已退单',
    `sort` = 3,
    `hidden` = 0,
    `update_by` = 'system',
    `update_time` = CURRENT_TIMESTAMP
WHERE LOWER(`dict`) = 'orderstatus'
  AND `value` = 'REFUNDED';

INSERT INTO `sys_dict_detail` (
    `id`, `dict`, `value`, `cn_desc`, `en_desc`, `sort`, `hidden`,
    `create_by`, `create_time`, `update_by`, `update_time`
)
SELECT
    2035000000000000001, 'orderStatus', 'REFUNDED', '已退单', '', 3, 0,
    'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM `sys_dict_detail`
    WHERE LOWER(`dict`) = 'orderstatus'
      AND `value` = 'REFUNDED'
);
