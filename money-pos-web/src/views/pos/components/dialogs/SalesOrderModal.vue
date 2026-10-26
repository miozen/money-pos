<template>
    <el-dialog v-model="visible" title="销售单查询与审计中心" width="1200px" top="3vh" destroy-on-close @closed="$emit('closed')">

        <div class="flex justify-between items-center mb-4 bg-gray-50 p-2 rounded border text-sm">
            <div class="flex items-center gap-4">
                <el-radio-group v-model="timeRange" size="default" @change="handleTimeChange">
                    <el-radio-button value="today">今天</el-radio-button>
                    <el-radio-button value="3days">近 3 天</el-radio-button>
                    <el-radio-button value="7days">近 7 天</el-radio-button>
                    <el-radio-button value="1month">近 1 个月</el-radio-button>
                </el-radio-group>

                <div class="flex items-center gap-6 px-4 py-1 bg-white rounded-lg border border-blue-100 shadow-sm ml-2">
                    <div class="flex flex-col items-center">
                        <span class="text-[10px] text-gray-400 font-bold uppercase">全局总单数</span>
                        <span class="text-lg font-black text-blue-600 leading-tight">{{ auditStats.orderCount || 0 }} <small class="text-[10px] font-normal text-gray-400">单</small></span>
                    </div>
                    <div class="w-px h-6 bg-gray-100"></div>
                    <div class="flex flex-col items-center">
                        <span class="text-[10px] text-gray-400 font-bold uppercase">后台权威实收 (已扣退款)</span>
                        <span class="text-lg font-black text-red-500 leading-tight">￥{{ Number(auditStats.totalSales || auditStats.saleCount || 0).toFixed(2) }}</span>
                    </div>
                </div>
            </div>

            <div class="flex gap-2 w-[280px]">
                <el-input v-model="searchKeyword" placeholder="输入订单号搜索" clearable @keyup.enter="refreshList" @clear="refreshList" size="default">
                    <template #append><el-button @click="refreshList"><el-icon><Search /></el-icon></el-button></template>
                </el-input>
            </div>
        </div>

        <div class="flex gap-4 h-[620px]">
            <div class="w-[32%] border rounded flex flex-col overflow-hidden shadow-sm bg-white">
                <el-table
                    ref="orderTableRef"
                    :data="orderList"
                    v-loading="loading && currentPage === 1"
                    stripe
                    highlight-current-row
                    height="100%"
                    class="w-full text-sm scrollable-table"
                    @current-change="handleSelectOrder"
                >
                    <el-table-column label="时间" width="80">
                        <template #default="{row}">
                            <div class="font-bold">{{ formatTime(row.createTime) }}</div>
                            <div class="text-[10px] text-gray-400">{{ formatDate(row.createTime) }}</div>
                        </template>
                    </el-table-column>
                    <el-table-column label="订单号" min-width="100" show-overflow-tooltip>
                        <template #default="{row}">
                            <span :class="['font-mono', (row.status === 'REFUNDED' || row.status === 'CLOSED') ? 'line-through text-gray-400' : '']">
                                {{ row.orderNo }}
                            </span>
                        </template>
                    </el-table-column>
                    <el-table-column label="实收" width="120" align="right">
                        <template #default="{row}">
                            <div class="whitespace-nowrap">
                                <span v-if="row.status === 'REFUNDED'" class="text-gray-400 font-bold">{{ row.statusDesc || '已退单' }}</span>
                                <span v-else-if="row.status === 'CLOSED'" class="text-gray-400 font-bold">{{ row.statusDesc || '已取消' }}</span>
                                <span v-else-if="row.status === 'PARTIAL_REFUNDED'" class="font-bold text-orange-500 tracking-tight">￥{{ Number(row.payAmount || 0).toFixed(2) }}</span>
                                <span v-else class="font-bold text-red-500 tracking-tight">￥{{ Number(row.payAmount || 0).toFixed(2) }}</span>
                            </div>
                        </template>
                    </el-table-column>
                </el-table>
                <div class="p-2 border-t bg-gray-50 flex justify-center items-center shrink-0">
                    <span v-if="loading && currentPage > 1" class="text-xs text-blue-500 font-bold"><el-icon class="is-loading mr-1"><Loading /></el-icon>正在加载更多...</span>
                    <span v-else-if="hasMore" class="text-[10px] text-gray-400 font-bold tracking-widest cursor-pointer hover:text-blue-500" @click="fetchOrders(true)">--- 向下滚动加载更多 ---</span>
                    <span v-else class="text-[10px] text-gray-400 font-bold tracking-widest">--- 已加载全部 {{ orderList.length }} 笔订单 ---</span>
                </div>
            </div>

            <div class="w-[68%] border rounded bg-white flex flex-col overflow-hidden relative shadow-sm">
                <div v-if="detailLoading" class="absolute inset-0 z-10 bg-white/70 flex items-center justify-center text-blue-500"><el-icon class="is-loading text-4xl"><Loading /></el-icon></div>
                <div v-if="!currentOrderDetail" class="h-full flex flex-col items-center justify-center text-gray-400 bg-gray-50">
                    <el-icon class="text-5xl mb-2"><Document /></el-icon><p>请在左侧选择一笔订单查看详情</p>
                </div>

                <div v-else class="h-full flex flex-col">
                    <div class="bg-gray-50 p-3 border-b flex justify-between items-center shrink-0">
                        <div>
                            <div class="font-bold text-gray-800 text-lg">单号: <span class="font-mono text-blue-600">{{ currentOrderDetail.orderNo }}</span></div>
                            <div class="text-xs text-gray-500 mt-1">创建时间: {{ currentOrderDetail.createTime }} | 操作人: {{ cashierName }}</div>
                        </div>
                        <el-tag effect="dark" :type="getOrderStatusType(currentOrderDetail.status)" class="tracking-widest font-bold">
                            {{ currentOrderDetail.statusDesc || getOrderStatusName(currentOrderDetail.status) }}
                        </el-tag>
                    </div>

                    <div class="flex-1 overflow-y-auto p-4 bg-white space-y-4">
                        <div class="border border-blue-100 rounded-lg overflow-hidden text-sm shadow-sm">
                            <div class="bg-blue-50 px-3 py-1.5 font-bold text-blue-700 border-b border-blue-100 flex items-center gap-2"><el-icon><User /></el-icon> 会员信息</div>
                            <div class="p-3 flex items-center gap-6">
                                <div class="shrink-0"><span class="text-gray-500 mr-2">姓名:</span><span class="font-bold text-gray-800">{{ currentOrderDetail.member?.name || currentOrderDetail.member || '散客' }}</span></div>
                                <div class="shrink-0"><span class="text-gray-500 mr-2">电话:</span><span class="font-mono text-gray-800">{{ currentOrderDetail.member?.phone || '-' }}</span></div>
                                <div class="flex flex-1 items-center gap-2 border-l border-gray-200 pl-4">
                                    <span class="text-gray-500 shrink-0">身份:</span>
                                    <div class="flex flex-wrap gap-2" v-if="currentOrderDetail.member?.brandLevelDesc && Object.keys(currentOrderDetail.member.brandLevelDesc).length > 0">
                                        <el-tag
                                            v-for="(levelName, brandName) in currentOrderDetail.member.brandLevelDesc"
                                            :key="brandName"
                                            size="small"
                                            type="success"
                                            effect="light"
                                            class="border-success-300"
                                        >
                                            <span class="font-bold text-gray-700 mr-1">{{ brandName }}</span>
                                            <span class="text-green-600 font-bold">{{ levelName }}</span>
                                        </el-tag>
                                    </div>
                                    <span v-else class="text-gray-400 font-bold text-sm">无关联身份 / 散客</span>
                                </div>
                            </div>
                        </div>

                        <div>
                            <div class="border border-gray-200 rounded-lg p-2.5 bg-gray-50 flex items-center justify-between shadow-inner overflow-x-auto">
                                <div class="flex flex-col items-center flex-1 px-0.5"><div class="text-[10px] text-gray-500 font-bold mb-0.5 whitespace-nowrap">应收</div><div class="text-sm font-black text-gray-800 whitespace-nowrap tracking-tight">￥{{ Number(currentOrderDetail.totalAmount || 0).toFixed(2) }}</div></div>
                                <div class="text-gray-300 font-black shrink-0 mx-1">-</div>
                                <div class="flex flex-col items-center flex-1 px-0.5" title="用户真实消耗的会员券资产"><div class="text-[10px] text-orange-500 font-bold mb-0.5 whitespace-nowrap">会员券核销</div><div class="text-sm font-black text-orange-500 whitespace-nowrap tracking-tight">￥{{ Number(currentOrderDetail.actualCouponDeduct || 0).toFixed(2) }}</div></div>
                                <div class="text-gray-300 font-black shrink-0 mx-1">-</div>
                                <div class="flex flex-col items-center flex-1 px-0.5" title="系统活动或免券导致的店铺让利"><div class="text-[10px] text-blue-500 font-bold mb-0.5 whitespace-nowrap">店铺免券让利</div><div class="text-sm font-black text-blue-500 whitespace-nowrap tracking-tight">￥{{ Number(currentOrderDetail.waivedCouponAmount || 0).toFixed(2) }}</div></div>
                                <div class="text-gray-300 font-black shrink-0 mx-1">-</div>
                                <div class="flex flex-col items-center flex-1 px-0.5"><div class="text-[10px] text-red-400 font-bold mb-0.5 whitespace-nowrap">满减券</div><div class="text-sm font-black text-red-500 whitespace-nowrap tracking-tight">￥{{ Number(currentOrderDetail.useVoucherAmount || 0).toFixed(2) }}</div></div>
                                <div class="text-gray-300 font-black shrink-0 mx-1">-</div>
                                <div class="flex flex-col items-center flex-1 px-0.5"><div class="text-[10px] text-red-400 font-bold mb-0.5 whitespace-nowrap">整单优惠</div><div class="text-sm font-black text-red-500 whitespace-nowrap tracking-tight">￥{{ Number(currentOrderDetail.manualDiscountAmount || 0).toFixed(2) }}</div></div>
                                <div class="text-gray-300 font-black shrink-0 mx-1">=</div>
                                <div class="flex flex-col items-center flex-1 px-0.5"><div class="text-[10px] text-blue-600 font-bold mb-0.5 whitespace-nowrap">实付</div><div class="text-sm font-black text-blue-600 whitespace-nowrap tracking-tight">￥{{ Number(currentOrderDetail.payAmount || 0).toFixed(2) }}</div></div>
                                <div class="text-gray-300 font-black shrink-0 mx-1">-</div>
                                <div class="flex flex-col items-center flex-1 px-0.5"><div class="text-[10px] text-gray-500 font-bold mb-0.5 whitespace-nowrap">退款</div><div class="text-sm font-black text-gray-600 whitespace-nowrap tracking-tight">￥{{ Number(returnPrice || 0).toFixed(2) }}</div></div>
                                <div class="text-gray-300 font-black shrink-0 mx-1">=</div>
                                <div class="bg-white border border-green-200 p-1.5 px-2 rounded shadow-sm text-center ml-1 shrink-0"><div class="text-[10px] text-green-600 font-black mb-0.5 whitespace-nowrap">净收</div><div class="text-base font-black text-green-500 whitespace-nowrap tracking-tighter">￥{{ Number(netIncome || 0).toFixed(2) }}</div></div>
                            </div>

                            <div class="mt-2 bg-blue-50/50 border border-blue-100 rounded-lg py-1.5 px-3 flex items-center justify-between text-[11px] shadow-sm">
                                <span class="text-blue-700 font-bold shrink-0">实付资金组成：</span>
                                <div class="flex gap-4 items-center flex-1 ml-2">
                                    <span>会员余额: <span class="font-bold text-gray-800">￥{{ Number(currentOrderDetail.balanceAmount || 0).toFixed(2) }}</span></span>
                                    <span class="flex items-center">
                                        聚合扫码: <span class="font-bold text-gray-800 ml-1">￥{{ Number(currentOrderDetail.scanAmount || 0).toFixed(2) }}</span>
                                        <el-tag
                                            v-for="(pay, index) in (currentOrderDetail.payList || []).filter(p => p.payMethodCode === 'AGGREGATE' && p.payAmount > 0)"
                                            :key="index"
                                            size="small"
                                            type="primary"
                                            effect="plain"
                                            class="ml-1 font-bold !h-[18px] !leading-[16px] !px-1.5 !text-[10px]"
                                        >
                                            {{ getPayTagName(pay.payTag) }}
                                        </el-tag>
                                    </span>
                                    <span>现金收银: <span class="font-bold text-gray-800">￥{{ Number(currentOrderDetail.cashAmount || 0).toFixed(2) }}</span></span>
                                </div>
                            </div>
                        </div>

                        <div class="font-bold text-gray-700 border-l-4 border-green-500 pl-2 mt-4">商品明细</div>
                        <el-table :data="currentOrderDetail.details" border size="small" class="w-full">
                            <el-table-column prop="goodsName" label="商品名称" show-overflow-tooltip min-width="120" />
                            <el-table-column label="商品条码" width="140">
                                <template #default="{row}"><span class="font-mono text-gray-500">{{ row.barcode || row.goodsBarcode || row.skuCode || row.skuBarcode || '-' }}</span></template>
                            </el-table-column>
                            <el-table-column label="成交价" width="90" align="right">
                                <template #default="{row}"><span class="font-bold text-red-500">￥{{ Number(row.goodsPrice || 0).toFixed(2) }}</span></template>
                            </el-table-column>
                            <el-table-column label="数量状态" width="80" align="center">
                                <template #default="{row}">
                                    <div class="font-bold">{{ row.quantity }}</div>
                                    <div v-if="row.returnQuantity > 0" class="text-[10px] text-red-500 font-bold">- 已退 {{ row.returnQuantity }}</div>
                                </template>
                            </el-table-column>
                            <el-table-column label="小计" width="100" align="right">
                                <template #default="{row}"><span class="font-bold">￥{{ Number(NP.times(row.quantity, row.goodsPrice)).toFixed(2) }}</span></template>
                            </el-table-column>
                            <el-table-column label="售后" width="70" align="center" fixed="right" v-if="currentOrderDetail.status !== 'REFUNDED' && currentOrderDetail.status !== 'CLOSED'">
                                <template #default="{row}">
                                    <el-button type="danger" link size="small" class="font-bold" @click="handlePartialReturn(row)" :disabled="(row.returnQuantity || 0) >= row.quantity">退货</el-button>
                                </template>
                            </el-table-column>
                        </el-table>
                    </div>

                    <div class="p-3 border-t bg-gray-50 flex justify-between items-center shrink-0">
                        <div class="flex gap-3">
                            <el-button type="primary" plain @click="reprintOrder"><el-icon class="mr-1"><Printer /></el-icon> 补打小票</el-button>
                            <el-button v-if="currentOrderDetail.status !== 'REFUNDED' && currentOrderDetail.status !== 'CLOSED'" type="danger" plain @click="handleFullReturn">
                                <el-icon class="mr-1"><RefreshLeft /></el-icon> 整单退款
                            </el-button>
                        </div>
                        <div class="text-right flex items-center gap-3">
                            <span class="text-sm font-bold text-gray-500 whitespace-nowrap">当前订单净收:</span>
                            <span class="text-3xl font-black text-green-600 tracking-tighter whitespace-nowrap">￥{{ Number(netIncome || 0).toFixed(2) }}</span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </el-dialog>
</template>

<script setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { Search, Document, Loading, Printer, RefreshLeft, User } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { req } from "@/api/index.js"
import { ElMessage, ElMessageBox } from 'element-plus'
import dictApi from "@/api/system/dict.js"
import NP from "number-precision"

const props = defineProps(['modelValue'])
const emit = defineEmits(['update:modelValue', 'closed'])
const visible = computed({ get: () => props.modelValue, set: (val) => emit('update:modelValue', val) })

const timeRange = ref('today')
const searchKeyword = ref('')

const orderTableRef = ref(null)
const orderList = ref([]);
const loading = ref(false);
const currentPage = ref(1);
const pageSize = ref(20);
const hasMore = ref(true);

const currentOrderDetail = ref(null);
const detailLoading = ref(false)
const payTagDict = ref([]);
const dict = ref({ orderStatusKv: {} });

const auditStats = ref({ orderCount: 0, totalSales: 0, profit: 0, saleCount: 0 })

const getTimeRangeParams = () => {
    const end = dayjs().endOf('day').format('YYYY-MM-DD HH:mm:ss')
    let start = dayjs().startOf('day').format('YYYY-MM-DD HH:mm:ss')
    if (timeRange.value === '3days') start = dayjs().subtract(2, 'day').startOf('day').format('YYYY-MM-DD HH:mm:ss')
    else if (timeRange.value === '7days') start = dayjs().subtract(6, 'day').startOf('day').format('YYYY-MM-DD HH:mm:ss')
    else if (timeRange.value === '1month') start = dayjs().subtract(1, 'month').startOf('day').format('YYYY-MM-DD HH:mm:ss')
    return { startTime: start, endTime: end }
}

const fetchStatistics = async () => {
    try {
        const { startTime, endTime } = getTimeRangeParams()
        const res = await req({ url: '/oms-order/statistics', method: 'GET', params: { startTime, endTime } })
        auditStats.value = res.data || res || { orderCount: 0, totalSales: 0 }
    } catch (e) {
        console.error("加载全局统计失败", e)
    }
}

const fetchOrders = async (isLoadMore = false) => {
    if (!isLoadMore) {
        currentPage.value = 1;
        orderList.value = [];
        hasMore.value = true;
        fetchStatistics();
    }
    if (!hasMore.value || loading.value) return;

    loading.value = true
    try {
        const { startTime, endTime } = getTimeRangeParams()
        const params = { current: currentPage.value, size: pageSize.value, startTime, endTime, orderNo: searchKeyword.value || undefined }
        const res = await req({ url: '/oms-order/page', method: 'GET', params })

        const records = res.data?.records || res.records || [];
        const total = res.data?.total || res.total || 0;

        if (isLoadMore) {
            orderList.value.push(...records);
        } else {
            orderList.value = records;
        }

        if (orderList.value.length >= total || records.length === 0) {
            hasMore.value = false;
        }
        currentPage.value++;
    } catch (e) { ElMessage.error('获取订单列表失败') } finally { loading.value = false }
}

const refreshList = () => { fetchOrders(false); }
const handleTimeChange = () => { refreshList(); }

onMounted(async () => {
    try {
        // 🌟 核心清理：不再请求 memberType，完全解耦前端字典依赖！
        const dictRes = await dictApi.loadDict(["paySubTag", "orderStatus"])
        if (dictRes) {
            if (dictRes.paySubTag) payTagDict.value = dictRes.paySubTag
            dict.value = dictRes
        }
    } catch (e) {}

    nextTick(() => {
        const wrap = orderTableRef.value?.$el.querySelector('.el-scrollbar__wrap');
        if (wrap) {
            wrap.addEventListener('scroll', (e) => {
                const { scrollTop, clientHeight, scrollHeight } = e.target;
                if (scrollTop + clientHeight >= scrollHeight - 10) {
                    if (hasMore.value && !loading.value) fetchOrders(true);
                }
            });
        }
    })
})

const handleSelectOrder = async (row) => {
    if (!row || !row.orderNo) return
    detailLoading.value = true
    currentOrderDetail.value = { ...row, details: [], log: [] }
    try {
        const res = await req({ url: '/oms-order/detail', method: 'GET', params: { orderNo: row.orderNo } })
        const data = res.data || res || {}
        currentOrderDetail.value = {
            ...row, ...data,
            details: data.orderDetails || [],
            member: data.memberInfo || data.member || {},
            log: data.orderLog || [],
            payList: data.payments || []
        }
    } catch (e) { ElMessage.error('获取明细失败') } finally { detailLoading.value = false }
}

const handlePartialReturn = async (row) => {
    try {
        const maxQty = row.quantity - (row.returnQuantity || 0)
        if (maxQty <= 0) return ElMessage.warning('该商品已全部退货')
        const { value } = await ElMessageBox.prompt(`请输入【${row.goodsName}】的退货数量 (最多可退 ${maxQty} 个)`, '商品退货', {
            confirmButtonText: '确定退货', cancelButtonText: '取消', inputPattern: /^[1-9]\d*$/, inputErrorMessage: '请输入正整数', inputValue: 1
        })
        const returnQty = parseInt(value)
        if (returnQty > maxQty) return ElMessage.error('不能超过可退数量！')
        detailLoading.value = true
        await req({
            url: '/oms-order/returnGoods',
            method: 'POST',
            data: {
                orderNo: currentOrderDetail.value.orderNo,
                detailId: row.id,
                returnQty: returnQty,
                reqId: 'PRET' + Date.now()
            }
        })
        ElMessage.success('退货成功！')
        handleSelectOrder(currentOrderDetail.value)
        refreshList();
    } catch (e) { if(e !== 'cancel') ElMessage.error(e.msg || '退货失败') } finally { detailLoading.value = false }
}

const handleFullReturn = async () => {
    try {
        await ElMessageBox.confirm('确定要将此订单【全额退款】吗？', '提示', { type: 'warning' })
        detailLoading.value = true
        await req({ url: '/oms-order/return', method: 'POST', data: { orderNo: currentOrderDetail.value.orderNo, reqId: 'RET' + Date.now() } })
        ElMessage.success('退单成功！')
        currentOrderDetail.value.status = 'REFUNDED'
        // 更新 statusDesc 保持前端表现一致
        currentOrderDetail.value.statusDesc = '已退单'
        refreshList();
    } catch (e) {} finally { detailLoading.value = false }
}

const reprintOrder = async () => {
    try { await req({ url: '/oms-order/hardware/print', method: 'GET', params: { orderNo: currentOrderDetail.value.orderNo } }); ElMessage.success('打印请求已发送'); } catch (e) {}
}

const getOrderStatusName = (s) => {
    // 仅作为最后一层安全兜底，正常情况下都由 row.statusDesc 接管
    const fallbackMap = {
        'UNPAID': '待支付',
        'PAID': '已支付',
        'PARTIAL_REFUNDED': '部分退款',
        'REFUNDED': '全额退款',
        'CLOSED': '已关闭',
        'CANCELLED': '已取消'
    };
    const match = dict.value.orderStatus?.find(v => v.value === s || v.dictValue === s);
    return match ? (match.desc || match.dictLabel) : (fallbackMap[s] || s);
}

const getOrderStatusType = (s) => {
    // 视觉样式依然由前端管控
    if (s === 'PAID') return 'success';
    if (s === 'PARTIAL_REFUNDED') return 'warning';
    if (s === 'REFUNDED' || s === 'CLOSED') return 'info';
    return 'danger';
}

const getPayTagName = (c) => payTagDict.value.find(t => t.value === c || t.dictValue === c)?.desc || '其他扫码';
const formatTime = (t) => t ? dayjs(t).format('HH:mm') : '';
const formatDate = (t) => t ? dayjs(t).format('MM-DD') : '';

const returnPrice = computed(() => (currentOrderDetail.value?.details || []).reduce((sum, i) => NP.plus(sum, NP.times(i.returnQuantity || 0, i.goodsPrice || 0)), 0));
const netIncome = computed(() => (currentOrderDetail.value?.status === 'REFUNDED' || currentOrderDetail.value?.status === 'CLOSED') ? 0 : NP.minus(currentOrderDetail.value?.payAmount || 0, returnPrice.value));
const cashierName = computed(() => { const logs = currentOrderDetail.value?.log || []; return logs.length > 0 ? logs[logs.length - 1].createBy : (currentOrderDetail.value?.createBy || 'System'); });

watch(visible, (v) => { if (v) refreshList() })
</script>

<style scoped>
.scrollable-table :deep(.el-scrollbar__wrap) {
    overflow-x: hidden;
}
</style>
