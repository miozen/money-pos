<template>
    <el-dialog v-model="visible" :title="`🧾 订单全局审计 - ${orderNo}`" width="1000px" top="5vh" destroy-on-close @closed="$emit('closed')">

        <div v-loading="detailLoading" class="min-h-[300px] p-2">
            <div v-if="!currentOrderDetail && !detailLoading" class="flex flex-col items-center justify-center text-gray-400 py-20">
                <el-icon class="text-5xl mb-2"><Document /></el-icon>
                <p>未能获取到该单据的底层快照明细</p>
            </div>

            <div v-else-if="currentOrderDetail" class="flex flex-col gap-6">
                <div class="border border-blue-100 rounded-lg overflow-hidden text-sm shadow-sm">
                    <div class="bg-blue-50 px-3 py-1.5 font-bold text-blue-700 border-b border-blue-100 flex items-center gap-2">
                        <el-icon><User /></el-icon> 会员信息
                    </div>
                    <div class="p-3 flex items-center gap-6">
                        <div class="shrink-0">
                            <span class="text-gray-500 mr-2">会员姓名:</span>
                            <span class="font-bold text-gray-800">{{ currentOrderDetail.member?.name || '-' }}</span>
                        </div>
                        <div class="shrink-0">
                            <span class="text-gray-500 mr-2">联系电话:</span>
                            <span class="font-mono text-gray-800">{{ currentOrderDetail.member?.phone || '-' }}</span>
                        </div>
                        <div class="flex flex-1 items-center gap-2 border-l border-gray-200 pl-4">
                            <span class="text-gray-500 shrink-0">会员身份:</span>
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
                    <div class="flex justify-between items-center mb-2">
                        <div class="font-bold text-gray-800 text-base">资金瀑布流 (强一致性核算)</div>
                        <div class="flex items-center gap-4 text-sm">
                            <el-tag effect="light" :type="getOrderStatusType(currentOrderDetail.status)" class="font-bold">
                                状态: {{ getOrderStatusName(currentOrderDetail.status) }}
                            </el-tag>
                            <span class="text-gray-500">交易时间: {{ currentOrderDetail.paymentTime || currentOrderDetail.createTime || '-' }}</span>
                        </div>
                    </div>

                    <el-descriptions :column="2" border>
                        <el-descriptions-item label="订单总额" label-align="center" align="center" label-class-name="whitespace-nowrap w-32">
                            <MoneyDisplay :value="currentOrderDetail.totalAmount" custom-class="text-gray-500 line-through" />
                        </el-descriptions-item>
                        <el-descriptions-item label="实付总计" label-align="center" align="center" label-class-name="whitespace-nowrap w-32">
                            <MoneyDisplay :value="currentOrderDetail.payAmount" size="lg" color="text-green-600" />
                        </el-descriptions-item>

                        <el-descriptions-item label="实收 (会员余额)" label-align="center" align="center" label-class-name="whitespace-nowrap w-32">
                            <MoneyDisplay :value="currentOrderDetail.balanceAmount" color="text-gray-800" />
                        </el-descriptions-item>

                        <el-descriptions-item label="实收 (聚合扫码)" label-align="center" align="center" label-class-name="whitespace-nowrap w-32">
                            <MoneyDisplay :value="currentOrderDetail.scanAmount" color="text-gray-800" />
                            <el-tag
                                v-for="(pay, index) in (currentOrderDetail.payList || []).filter(p => p.payMethodCode === 'AGGREGATE' && p.payAmount > 0)"
                                :key="index"
                                size="small"
                                type="primary"
                                effect="plain"
                                class="ml-2 font-bold"
                            >
                                {{ getPayTagName(pay.payTag) }}
                            </el-tag>
                        </el-descriptions-item>

                        <el-descriptions-item label="实收 (现金)" label-align="center" align="center" label-class-name="whitespace-nowrap w-32">
                            <MoneyDisplay :value="currentOrderDetail.cashAmount" color="text-gray-800" />
                        </el-descriptions-item>

                        <el-descriptions-item label="整单总成本" label-align="center" align="center" label-class-name="whitespace-nowrap w-32">
                            <MoneyDisplay :value="currentOrderDetail.costAmount" color="text-blue-600" />
                        </el-descriptions-item>

                        <el-descriptions-item label="会员券真实核销" label-align="center" align="center" label-class-name="whitespace-nowrap w-32">
                            <span class="text-red-500 font-bold mr-1">-</span><MoneyDisplay :value="currentOrderDetail.actualCouponDeduct" color="text-red-500" />
                        </el-descriptions-item>
                        <el-descriptions-item label="店铺免券让利" label-align="center" align="center" label-class-name="whitespace-nowrap w-32">
                            <span class="text-blue-500 font-bold mr-1">-</span><MoneyDisplay :value="currentOrderDetail.waivedCouponAmount" color="text-blue-500" />
                        </el-descriptions-item>

                        <el-descriptions-item label="满减券抵扣" label-align="center" align="center" label-class-name="whitespace-nowrap w-32">
                            <span class="text-red-500 font-bold mr-1">-</span><MoneyDisplay :value="currentOrderDetail.useVoucherAmount" color="text-red-500" />
                        </el-descriptions-item>

                        <el-descriptions-item label="整单优惠" label-align="center" align="center" label-class-name="whitespace-nowrap w-32">
                            <span class="text-red-500 font-bold mr-1">-</span><MoneyDisplay :value="currentOrderDetail.manualDiscountAmount" color="text-red-500" />
                        </el-descriptions-item>
                    </el-descriptions>
                </div>

                <div>
                    <div class="font-bold text-gray-800 text-base mb-2">商品物理快照</div>
                    <el-table :data="currentOrderDetail.details || []" stripe border size="small" class="w-full font-mono">
                        <el-table-column prop="goodsName" label="商品名称" min-width="150" show-overflow-tooltip />

                        <el-table-column label="商品条码" width="140">
                            <template #default="{row}">
                                <span class="font-mono text-gray-500">{{ row.barcode || row.goodsBarcode || row.skuCode || row.skuBarcode || '-' }}</span>
                            </template>
                        </el-table-column>

                        <el-table-column prop="salePrice" label="吊牌单价" width="100" align="right">
                            <template #default="{row}"><MoneyDisplay :value="row.salePrice" /></template>
                        </el-table-column>

                        <el-table-column prop="goodsPrice" label="实际单价" width="100" align="right">
                            <template #default="{row}"><MoneyDisplay :value="row.goodsPrice" color="text-green-600" /></template>
                        </el-table-column>

                        <el-table-column prop="quantity" label="购买" width="60" align="center" />
                        <el-table-column prop="returnQuantity" label="退回" width="60" align="center">
                            <template #default="{row}">
                                <span :class="row.returnQuantity > 0 ? 'text-red-500 font-bold' : 'text-gray-400'">{{ row.returnQuantity || 0 }}</span>
                            </template>
                        </el-table-column>

                        <el-table-column prop="purchasePrice" label="锁定成本" width="100" align="right">
                            <template #default="{row}"><MoneyDisplay :value="row.purchasePrice" /></template>
                        </el-table-column>
                    </el-table>
                </div>

                <div v-if="currentOrderDetail.log && currentOrderDetail.log.length > 0">
                    <div class="font-bold text-gray-800 text-base mb-2">防篡改操作审计日志</div>
                    <el-timeline class="pl-2 mt-2">
                        <el-timeline-item
                            v-for="(log, index) in currentOrderDetail.log"
                            :key="index"
                            :timestamp="log.createTime"
                            size="small"
                            type="info"
                        >
                            <span class="text-gray-600 text-sm font-mono whitespace-pre-wrap leading-relaxed">{{ formatLogMessage(log.description) }}</span>
                        </el-timeline-item>
                    </el-timeline>
                </div>
            </div>
        </div>
    </el-dialog>
</template>

<script setup>
import { ref, computed, watch, onBeforeMount } from 'vue'
import { Document, User } from '@element-plus/icons-vue'
import { req } from "@/api/index.js"
import dictApi from "@/api/system/dict.js"
import { ElMessage } from 'element-plus'

const props = defineProps(['modelValue', 'orderNo'])
const emit = defineEmits(['update:modelValue', 'closed'])
const visible = computed({ get: () => props.modelValue, set: (val) => emit('update:modelValue', val) })

const currentOrderDetail = ref(null)
const detailLoading = ref(false)

const dict = ref({})

const fetchBaseData = async () => {
    try {
        // 🌟 净化：移除了 memberType 的加载，只保留支付方式和订单状态字典
        const dictRes = await dictApi.loadDict(["paySubTag", "orderStatus"])
        dict.value = dictRes || {}
    } catch (e) {
        console.error("字典加载失败", e)
    }
}

onBeforeMount(() => {
    fetchBaseData()
})

const getPayTagName = (tagCode) => {
    if (!tagCode) return '其他扫码'
    const tags = dict.value.paySubTag
    if (!Array.isArray(tags) || tags.length === 0) return tagCode

    const match = tags.find(t => t && (t.value === tagCode || t.dictValue === tagCode))
    return match ? (match.desc || match.dictLabel || tagCode) : tagCode
}

const getOrderStatusName = (status) => {
    if (!status) return '-';
    const statuses = dict.value.orderStatus;
    const fallbackMap = {
        'PENDING': '待支付', 'PAID': '已支付', 'PARTIAL': '部分退款',
        'RETURN': '全额退款', 'REFUNDED': '已退款', 'CLOSED': '已关闭', 'CANCELLED': '已取消'
    };
    if (!Array.isArray(statuses) || statuses.length === 0) return fallbackMap[status] || status;
    const match = statuses.find(s => s && (s.value === status || s.dictValue === status));
    return match ? (match.desc || match.dictLabel || status) : (fallbackMap[status] || status);
}

const getOrderStatusType = (status) => {
    if (status === 'PAID') return 'success';
    if (status === 'PARTIAL') return 'warning';
    if (status === 'RETURN' || status === 'REFUNDED' || status === 'CLOSED' || status === 'CANCELLED') return 'info';
    return 'danger';
}

const translatePayMethods = (methodsStr) => {
    if (!methodsStr) return '';
    return methodsStr.split(',').map(method => {
        if (method === 'CASH') return '现金';
        if (method === 'BALANCE') return '会员余额';
        if (method.startsWith('AGGREGATE:')) {
            const tag = method.split(':')[1];
            return `扫码(${getPayTagName(tag)})`;
        }
        if (method === 'AGGREGATE') return '聚合扫码';
        return method;
    }).join(' + ');
}

const formatLogMessage = (desc) => {
    if (!desc) return '-';
    if (desc.startsWith('{') && desc.endsWith('}')) {
        try {
            const obj = JSON.parse(desc);
            let text = '';
            if (obj.action === 'SETTLE_SUCCESS') text += '✅ 收银结算成功\n';
            if (obj.payMethods) {
                text += `[支付组合]: ${translatePayMethods(obj.payMethods)}\n`;
            }
            if (obj.totalPaid !== undefined) text += `[应付金额]: ￥${obj.totalPaid}\n`;
            if (obj.finalPay !== undefined) text += `[实付金额]: ￥${obj.finalPay}\n`;
            if (obj.change !== undefined) text += `[找零金额]: ￥${obj.change}\n`;
            return text || desc;
        } catch (e) {
            return desc;
        }
    }
    return desc;
}

const returnPrice = computed(() => {
    if (!currentOrderDetail.value || !currentOrderDetail.value.details) return 0;
    return currentOrderDetail.value.details.reduce((sum, item) => sum + ((item.returnQuantity || 0) * (item.goodsPrice || 0)), 0);
})

watch(visible, async (newVal) => {
    if (newVal && props.orderNo) {
        if (!dict.value.orderStatus) {
            await fetchBaseData()
        }

        currentOrderDetail.value = null;
        detailLoading.value = true;
        try {
            const res = await req({ url: '/oms-order/detail', method: 'GET', params: { orderNo: props.orderNo } })
            const data = res?.data || res || {}

            currentOrderDetail.value = {
                ...data,
                details: data.orderDetails || [],
                member: data.memberInfo || data.member || {},
                log: data.orderLog || [],
                payList: data.payments || []
            }
        } catch (e) {
            ElMessage.error('获取订单详情失败，请稍后重试')
        } finally {
            detailLoading.value = false
        }
    }
})
</script>
