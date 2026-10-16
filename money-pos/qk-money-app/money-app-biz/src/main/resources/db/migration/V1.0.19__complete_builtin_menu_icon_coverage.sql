UPDATE sys_permission
SET icon = CASE
  WHEN router_path = 'gmsbrand' THEN 'el:Collection'
  WHEN router_path = 'gmscombo' THEN 'el:Box'
  WHEN router_path IN ('gmsinbound', 'gmscheck', 'gmsoutbound', 'gmsstockLog') THEN 'el:Box'
  WHEN router_path IN ('member-log', 'financechannel') THEN 'el:Wallet'
  WHEN router_path IN ('memberRecall', 'umsrank') THEN 'el:Avatar'
  WHEN router_path IN ('oms/audit', 'financerisk', 'gmsturnover') THEN 'el:WarningFilled'
  WHEN router_path IN ('oms/analysis', 'gmsanalysis', 'omsanalysistraffic', 'omsanalysistide', 'marketing-roi') THEN 'el:DataAnalysis'
  WHEN router_path IN ('printconfig') THEN 'el:Printer'
  WHEN router_path IN ('customerDisplay') THEN 'el:Monitor'
  WHEN router_path IN ('Systembackup') THEN 'el:Files'
  WHEN router_path IN ('systemstrategy') THEN 'el:Setting'
  WHEN router_path = 'DecisionDashboards' THEN 'el:Histogram'
  WHEN router_path IN ('financeprofit-ranking', 'financeshifthandover') THEN 'el:TrendCharts'
  WHEN router_path = 'couponRule' THEN 'el:Ticket'
  ELSE icon
END
WHERE permission_type IN ('DIR', 'MENU');
