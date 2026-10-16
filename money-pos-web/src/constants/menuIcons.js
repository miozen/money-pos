import {
    Avatar, Box, Calendar, Collection, Coin, DataAnalysis, Document, Download, Files, Goods, Histogram,
    Key, Medal, Monitor, Money, OfficeBuilding, PieChart, Present, Printer, RefreshLeft, Setting,
    ShoppingCart, Ticket, TrendCharts, Upload, User, UserFilled, Wallet, WarningFilled,
} from '@element-plus/icons-vue';

export const builtInMenuIcons = [
    ['系统设置', 'Setting', Setting], ['用户', 'User', User], ['用户组', 'UserFilled', UserFilled], ['权限密钥', 'Key', Key],
    ['字典集合', 'Collection', Collection], ['租户/门店', 'OfficeBuilding', OfficeBuilding], ['监控/设备', 'Monitor', Monitor],
    ['商品', 'Goods', Goods], ['库存/箱', 'Box', Box], ['会员', 'Avatar', Avatar], ['权益/礼品', 'Present', Present],
    ['等级/奖牌', 'Medal', Medal], ['券', 'Ticket', Ticket], ['收银/金额', 'Money', Money], ['余额/钱包', 'Wallet', Wallet],
    ['订单/单据', 'Document', Document], ['营销/购物车', 'ShoppingCart', ShoppingCart], ['经营分析', 'DataAnalysis', DataAnalysis],
    ['趋势分析', 'TrendCharts', TrendCharts], ['统计图表', 'Histogram', Histogram], ['占比图表', 'PieChart', PieChart],
    ['日历', 'Calendar', Calendar], ['打印', 'Printer', Printer], ['文件', 'Files', Files], ['导入', 'Upload', Upload],
    ['导出', 'Download', Download], ['退货/还原', 'RefreshLeft', RefreshLeft], ['告警', 'WarningFilled', WarningFilled], ['支付', 'Coin', Coin],
].map(([label, name, component]) => ({ label, value: `el:${name}`, component }));

export const builtInMenuIconMap = Object.fromEntries(builtInMenuIcons.map(icon => [icon.value, icon.component]));
