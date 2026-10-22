UPDATE `cloud_member_sync_config`
SET `store_id` = 'store-01'
WHERE `store_id` IS NULL OR TRIM(`store_id`) = '';
