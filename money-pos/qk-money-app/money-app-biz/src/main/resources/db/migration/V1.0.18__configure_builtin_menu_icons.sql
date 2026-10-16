UPDATE sys_permission
SET icon = CASE
  WHEN permission_name = '系统管理' THEN 'el:Setting'
  WHEN permission_name = '用户管理' THEN 'el:User'
  WHEN permission_name = '角色管理' THEN 'el:UserFilled'
  WHEN permission_name = '权限管理' THEN 'el:Key'
  WHEN permission_name = '字典管理' THEN 'el:Collection'
  WHEN permission_name = '租户管理' THEN 'el:OfficeBuilding'
  WHEN permission_name IN ('会员管理', '会员档案') THEN 'el:Avatar'
  WHEN permission_name IN ('会员权益', '金额权益包管理') THEN 'el:Present'
  WHEN permission_name = '权益档位管理' THEN 'el:Medal'
  WHEN permission_name IN ('商品管理', '商品') THEN 'el:Goods'
  WHEN permission_name IN ('库存管理', '套餐管理') THEN 'el:Box'
  WHEN permission_name = '营销管理' THEN 'el:ShoppingCart'
  WHEN permission_name IN ('销售单据', '订单详情') THEN 'el:Document'
  WHEN permission_name = '收银台' THEN 'el:Money'
  WHEN permission_name = '财务管理' THEN 'el:DataAnalysis'
  WHEN permission_name IN ('今日财务大屏', '经营业绩汇总报表') THEN 'el:TrendCharts'
  WHEN permission_name IN ('备份还原', '系统备份') THEN 'el:Files'
  ELSE icon
END
WHERE permission_type IN ('DIR', 'MENU');
