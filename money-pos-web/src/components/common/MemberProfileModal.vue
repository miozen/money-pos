<template>
    <el-dialog
        v-model="visible"
        :title="`🌟 ${localMemberInfo.name || '加载中...'} 的全景画像`"
        width="550px"
        align-center
        destroy-on-close
        class="rounded-2xl member-profile-dialog"
    >
        <div class="px-2 pb-2">
            <el-tabs v-model="activeTab" class="custom-tabs">
                <el-tab-pane name="profile">
                    <template #label>
                        <span class="flex items-center gap-1"><el-icon><User /></el-icon> 消费画像</span>
                    </template>

                    <div class="flex flex-col gap-5 mt-2">
                        <div class="bg-gradient-to-br from-blue-50 to-indigo-50 p-5 rounded-xl border border-blue-100 shadow-sm relative overflow-hidden" v-loading="profileLoading">
                            <div class="absolute right-[-10px] top-[-10px] opacity-10 text-6xl"><el-icon><Trophy /></el-icon></div>
                            <div class="text-sm text-gray-500 font-bold mb-1">历史累计消费金额</div>
                            <div class="text-3xl font-black text-blue-600 mb-3">￥{{ (localMemberInfo.consumeAmount || 0).toFixed(2) }}</div>

                            <div class="flex flex-col gap-2 text-sm text-gray-600 border-t border-blue-100/50 pt-3">
                                <div class="flex justify-between">
                                    <span>📱 手机号码:</span> <span class="font-mono font-bold">{{ localMemberInfo.phone || '-' }}</span>
                                </div>
                                <div class="flex justify-between">
                                    <span>💳 会员余额:</span> <span class="font-bold text-gray-800">￥{{ (localMemberInfo.balance || 0).toFixed(2) }}</span>
                                </div>
                                <div class="flex justify-between">
                                    <span>🎫 会员券:</span> <span class="font-bold text-orange-500">￥{{ (localMemberInfo.coupon || 0).toFixed(2) }}</span>
                                </div>
                                <div class="flex justify-between">
                                    <span>🎟️ 剩余满减券:</span> <span class="font-bold text-red-500">{{ localMemberInfo.voucherCount || 0 }} 张</span>
                                </div>

                                <div class="flex justify-between mt-2 pt-2 border-t border-blue-100/50 items-start">
                                    <span class="text-blue-500 font-bold whitespace-nowrap mt-1">特权版图:</span>
                                    <div class="flex flex-wrap gap-1 justify-end max-w-[220px]">
                                        <template v-if="localMemberInfo.brandLevelDesc && Object.keys(localMemberInfo.brandLevelDesc).length > 0">
                                            <el-tag v-for="(levelName, brandName) in localMemberInfo.brandLevelDesc" :key="brandName" size="small" effect="dark" type="success" class="border-0 shadow-sm">
                                                {{ brandName }}: {{ levelName }}
                                            </el-tag>
                                        </template>
                                        <span v-else class="text-gray-400 border border-gray-300 border-dashed px-2 py-0.5 rounded text-xs">仅限普通零售</span>
                                    </div>
                                </div>
                                <div class="flex justify-between mt-2 pt-2 border-t border-blue-100/50">
                                    <span class="text-blue-500">最后到店时间:</span>
                                    <span class="font-bold text-blue-600">{{ localMemberInfo.lastVisitTime || '暂无消费记录' }}</span>
                                </div>
                            </div>
                        </div>

                        <div>
                            <h3 class="font-bold text-gray-800 mb-3 flex items-center gap-2 text-base">
                                <el-icon class="text-orange-500"><Histogram /></el-icon> 核心购物偏好 Top 20
                            </h3>
                            <el-table :data="topList" size="small" height="250" stripe border class="w-full shadow-sm rounded-lg overflow-hidden" v-loading="topLoading">
                                <el-table-column type="index" label="排" width="45" align="center">
                                    <template #default="scope">
                                        <span :class="{'text-red-500 font-black': scope.$index === 0, 'text-orange-500 font-bold': scope.$index === 1, 'text-yellow-500 font-bold': scope.$index === 2, 'text-gray-400': scope.$index > 2}">
                                            {{ scope.$index + 1 }}
                                        </span>
                                    </template>
                                </el-table-column>
                                <el-table-column prop="goodsName" label="商品名称" show-overflow-tooltip />
                                <el-table-column prop="buyCount" label="累计购买(件)" width="90" align="center">
                                    <template #default="{row}"><span class="font-black text-blue-600">{{ row.buyCount }}</span></template>
                                </el-table-column>
                            </el-table>
                        </div>
                    </div>
                </el-tab-pane>

                <el-tab-pane name="logs">
                    <template #label>
                        <span class="flex items-center gap-1"><el-icon><List /></el-icon> 资产与权益变动</span>
                    </template>
                    <div class="mt-2">
                        <el-table :data="logList" size="small" height="420px" border stripe v-loading="logLoading">
                            <el-table-column prop="createTime" label="变动时间" width="140" align="center" />
                            <el-table-column prop="type" label="资产/权益" width="105" align="center">
                                <template #default="{row}">
                                    <el-tag size="small" :type="getAssetTagType(row.type)">{{ getAssetTypeName(row.type) }}</el-tag>
                                </template>
                            </el-table-column>
                            <el-table-column prop="amount" label="变动金额" width="90" align="right">
                                <template #default="{row}">
                                    <span :class="row.amount > 0 ? 'text-green-600 font-bold' : 'text-red-500 font-bold'">
                                        {{ row.amount > 0 ? '+' : '' }}{{ row.amount.toFixed(2) }}
                                    </span>
                                </template>
                            </el-table-column>
                            <el-table-column prop="orderNo" label="关联单据" min-width="120">
                                <template #default="{row}">
                                    <el-link v-if="row.orderNo" type="primary" :underline="false" @click="showOrderDetail(row)" class="font-mono text-xs">
                                        {{ row.orderNo }}
                                    </el-link>
                                    <span v-else class="text-gray-300">-</span>
                                </template>
                            </el-table-column>
                            <el-table-column prop="remark" label="摘要说明" show-overflow-tooltip />
                        </el-table>
                    </div>
                </el-tab-pane>
            </el-tabs>
        </div>

        <RechargeOrderDetail v-model="rechargeDetailVisible" :order-no="currentOrderNo" @refresh="refreshAfterVoid" />
        <OrderDetailModal v-model="salesDetailVisible" :order-no="currentOrderNo" />
        <el-dialog v-model="pickupReceiptVisible" title="会员提货单" width="560px" append-to-body>
            <el-descriptions v-if="pickupReceipt" :column="2" border><el-descriptions-item label="提货单号">{{ pickupReceipt.pickupNo }}</el-descriptions-item><el-descriptions-item label="会员编号">{{ pickupReceipt.memberId }}</el-descriptions-item><el-descriptions-item label="办理时间">{{ pickupReceipt.pickupTime }}</el-descriptions-item><el-descriptions-item label="操作员">{{ pickupReceipt.operatorName }}</el-descriptions-item></el-descriptions>
            <el-table v-if="pickupReceipt" class="mt-3" :data="pickupReceipt.lines || []" border><el-table-column prop="goodsName" label="商品"/><el-table-column prop="goodsBarcode" label="条码"/><el-table-column prop="quantity" label="数量" width="80"/></el-table>
        </el-dialog>
    </el-dialog>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { ref, watch } from 'vue'
import { Trophy, User, List, Histogram } from '@element-plus/icons-vue'
import { req } from "@/api/index.js"
import RechargeOrderDetail from "@/views/ums/member/components/RechargeOrderDetail.vue"
import OrderDetailModal from "@/components/OrderDetailModal.vue"

const props = defineProps({
    modelValue: { type: Boolean, default: false },
    memberId: { type: [Number, String], default: null }, // 🌟 核心：现在对外只需要这个身份证号！
    memberInfo: { type: Object, default: () => ({}) },   // 作为初始占位数据，防止刚打开时弹窗一片空白
    brandsDict: { type: Object, default: () => ({}) },
    levelsDict: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['update:modelValue'])

const visible = ref(props.modelValue)
const activeTab = ref('profile')

// 🌟 组件内部维护的三大数据加载状态
const profileLoading = ref(false)
const topLoading = ref(false)
const logLoading = ref(false)

const localMemberInfo = ref({}) // 🌟 组件自己内聚的“胖模型”
const topList = ref([])
const logList = ref([])

const rechargeDetailVisible = ref(false)
const salesDetailVisible = ref(false)
const currentOrderNo = ref('')
const pickupReceiptVisible = ref(false), pickupReceipt = ref(null)

const getAssetTypeName = (type) => {
    if (type === 'BALANCE') return '会员余额';
    if (type === 'COUPON') return '会员券';
    if (type === 'VOUCHER') return '满减券';
    if (type === 'QUANTITY') return '数量权益';
    if (type === 'AMOUNT') return '金额权益';
    if (type === 'TARGET_PROGRESS') return '升级进度';
    if (type === 'TRADE_DOCUMENT') return '业务单据';
    return type || '未知';
}

const getAssetTagType = (type) => {
    if (type === 'BALANCE') return '';
    if (type === 'COUPON') return 'warning';
    if (type === 'VOUCHER') return 'danger';
    return 'info';
}

// 🌟 监听弹窗打开，自主执行三大查询任务！
watch(() => props.modelValue, (newVal) => {
    visible.value = newVal
    if (newVal) {
        activeTab.value = 'profile'

        // 1. 先把传进来的基础信息（名字、ID等）塞进去，让标题瞬间出来，不要发呆
        localMemberInfo.value = { ...props.memberInfo }

        // 2. 提取最核心的 targetId
        const targetId = props.memberId || props.memberInfo?.id;

        if (targetId) {
            // 3. 兵分两路：自己去查档案和 Top 榜单！
            fetchFullProfile(targetId);
            fetchTopGoods(targetId);
        }
    }
})

watch(activeTab, (val) => {
    const targetId = props.memberId || props.memberInfo?.id;
    if (val === 'logs' && targetId) fetchLogs(targetId)
})

watch(visible, (newVal) => {
    emit('update:modelValue', newVal)
})

// 🌟 新增：独立自主拉取完整会员档案
const fetchFullProfile = async (memberId) => {
    profileLoading.value = true
    try {
        // 注：这里假设后端的单条详情接口是 GET /ums/member/{id}
        // 或者是 /ums/member/detail?id=xxx，请根据您的实际接口微调
        const res = await req({ url: `/ums/member/${memberId}`, method: 'GET' })
        // 合并数据：用后端返回的完整胖模型覆盖掉初始的残缺数据
        localMemberInfo.value = Object.assign({}, localMemberInfo.value, res.data || res)
    } catch (e) {
        console.error("自主拉取会员完整档案失败", e)
    } finally {
        profileLoading.value = false
    }
}

const fetchTopGoods = async (memberId) => {
    topLoading.value = true
    try {
        const res = await req({ url: '/ums/member/top20Goods', method: 'GET', params: { memberId } })
        topList.value = res.data || res || []
    } finally {
        topLoading.value = false
    }
}

const fetchLogs = async (memberId) => {
    logLoading.value = true
    try {
        const res = await req({ url: '/ums/member/asset-benefit-history', method: 'GET', params: { memberId } })
        const records = (res.data || res || {}).records || []
        logList.value = records.map(row => ({ ...row, type: row.dimension, amount: Number(row.delta || 0), orderNo: row.referenceNo, remark: summaryText(row), operateType: row.businessType }))
    } finally {
        logLoading.value = false
    }
}

const showOrderDetail = (row) => {
    if (!row?.orderNo) return
    if (row.detailTarget === 'PICKUP') return previewPickupReceipt(row)
    currentOrderNo.value = row.orderNo
    if (row.detailTarget === 'RECHARGE' || (!row.detailTarget && row.operateType === 'RECHARGE')) {
        rechargeDetailVisible.value = true
    } else {
        salesDetailVisible.value = true
    }
}

const previewPickupReceipt = async (row) => {
    const url = row.type === 'AMOUNT' ? '/pos/amount-package/pickup-receipt' : '/pos/deferred-quantity/pickup-receipt'
    try {
        const res = await req({ url, method: 'GET', params: { pickupNo: row.orderNo } })
        pickupReceipt.value = res.data || res
        pickupReceiptVisible.value = true
    } catch (e) {
        ElMessage.error('获取提货单失败，请稍后重试')
    }
}
const summaryText = (row) => ({
    RECHARGE: '会员充值', CONSUME: '会员消费', IMPORT: '导入会员资产', GIFT: '赠送会员资产', ISSUE: '发放会员券', REVERSAL: '冲正会员资产',
    GRANT: '权益办理', PICKUP_DEDUCT: '会员提货', PICKUP_REFUND: '提货退回', PICKUP_REFUND_RESTORE: '提货退款恢复权益', REFUND_CANCEL: '退款扣回未提货权益', SALE_CONTRIBUTION: '订单计入升级进度',
    SUPPLEMENT: '升级补差', WAIVER: '升级豁免', MANUAL_CONFIRM: '人工确认升级', CANCELLED: '取消升级计划',
    INITIAL_PROGRESS: '建立升级计划', REFUND_REVIEW_REQUIRED: '退款待人工复核', AMOUNT_PICKUP: '金额权益提货', QUANTITY_PICKUP: '数量权益提货',
    AMOUNT_PACKAGE_PURCHASE: '办理金额权益包'
}[row.businessType] || row.summary || '会员权益业务')

const refreshAfterVoid = () => {
    const targetId = props.memberId || props.memberInfo?.id;
    if (targetId) fetchLogs(targetId);
}
const getBrandName = (brandId) => props.brandsDict[brandId] || '未知品牌'
const getLevelName = (levelCode) => props.levelsDict[levelCode] || levelCode
</script>

<style scoped>
.member-profile-dialog :deep(.el-dialog__body) { padding-top: 10px; }
.custom-tabs :deep(.el-tabs__nav-wrap::after) { height: 1px; }
</style>
