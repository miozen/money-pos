<template>
    <el-dialog v-model="visible" title="混合收银工作站" width="900px" top="6vh" destroy-on-close class="checkout-dialog" @closed="handleClosed">
        <el-alert
            v-if="stockErrorMessage"
            :title="stockErrorMessage"
            type="error"
            show-icon
            closable
            class="mx-4 mt-4"
            @close="stockErrorMessage = ''"
        />
        <div class="flex gap-4 p-4 pb-0 h-[480px]">
            <div class="w-[42%] flex flex-col gap-2">
                <div v-if="currentMember.id" class="bg-gradient-to-br from-blue-50 to-indigo-50 p-3 rounded-lg border border-blue-200 shadow-inner shrink-0">
                    <div class="flex items-center gap-3 mb-2 border-b border-blue-100 pb-2">
                        <el-avatar :size="40" class="bg-blue-500 font-bold">{{ currentMember.name?.charAt(0) || 'V' }}</el-avatar>
                        <div class="overflow-hidden">
                            <div class="font-black text-base text-gray-800 truncate">{{ currentMember.name }}</div>
                            <div class="text-xs text-gray-500 mt-0.5 flex items-center gap-2">
                                <span class="bg-blue-100 text-blue-700 px-1.5 py-0.5 rounded font-bold border border-blue-200">{{ memberLevelDesc || '会员' }}</span>
                                <span class="font-mono">{{ currentMember.phone }}</span>
                            </div>
                        </div>
                    </div>
                    <div class="space-y-1.5">
                        <div class="flex justify-between items-center bg-white/60 px-2 py-1 rounded">
                            <span class="text-xs text-gray-600 font-bold">会员余额</span><span class="text-base font-black text-blue-600">￥{{ (currentMember.balance || 0).toFixed(2) }}</span>
                        </div>
                        <div class="flex justify-between items-center bg-white/60 px-2 py-1 rounded">
                            <span class="text-xs text-gray-600 font-bold">会员券 (抵扣)</span><span class="text-base font-black text-teal-600">￥{{ (currentMember.coupon || 0).toFixed(2) }}</span>
                        </div>
                        <div class="flex justify-between items-center bg-white/60 px-2 py-1 rounded">
                            <span class="text-xs text-gray-600 font-bold">拥有满减券</span><span class="text-base font-bold text-orange-500">{{ currentMember.voucherCount || 0 }} 张</span>
                        </div>
                    </div>
                </div>
                <div v-else class="bg-gray-50 p-4 rounded-lg border border-dashed flex flex-col items-center justify-center text-gray-400 h-[180px] shrink-0">
                    <el-icon :size="40" class="mb-2"><UserFilled /></el-icon><p class="tracking-widest font-bold">普通散客，无会员特权</p>
                </div>

                <div class="flex-1 bg-gray-50 p-3.5 rounded-lg border flex flex-col justify-between">
                    <div class="flex flex-col gap-2 text-orange-600">
                        <div class="flex justify-between items-center">
                            <span class="font-bold flex items-center gap-1 whitespace-nowrap"><el-icon><Ticket /></el-icon> 满减券</span>
                            <el-select v-model="selectedCouponRule" placeholder="请选择使用券" class="w-[160px]" size="default" @change="handleCouponRuleChange" clearable>
                                <el-option v-for="c in availableCoupons" :key="c.ruleId" :label="c.name" :value="c" />
                            </el-select>
                        </div>
                        <div class="flex justify-between items-center mt-1 min-h-[32px]">
                            <template v-if="selectedCouponRule">
                                <span class="text-xs text-green-600 font-bold">最多可用 {{ maxUsableCoupons }} 张</span>
                                <div class="flex items-center gap-2">
                                    <span class="text-xs text-gray-500 whitespace-nowrap">使用:</span>
                                    <el-input-number v-model="usedCouponCount" :min="1" :max="maxUsableCoupons" :step="1" class="!w-[90px]" size="small" @change="handleDiscountChange" @focus="handleFocus" />
                                </div>
                            </template>
                        </div>
                        <div v-if="currentMember.id" class="text-[10px] text-gray-400 text-right">
                            当前符合活动金额: ￥{{ participatingAmount.toFixed(2) }}
                        </div>
                    </div>

                    <div class="flex flex-col mt-2">
                        <div class="flex justify-between items-center text-blue-600 border-t border-gray-200 pt-3">
                            <span class="font-bold whitespace-nowrap">🏷️ 整单优惠:</span>
                            <el-input-number v-model="manualDiscount" :min="0" :max="totalAmount" :precision="2" :step="1" class="!w-[130px]" placeholder="直减" @change="handleDiscountChange" @focus="handleFocus" />
                        </div>

                        <div class="flex flex-col border-t border-dashed border-gray-300 pt-3 mt-3 min-h-[45px]" v-show="currentMember.id && theoreticalCouponUsed > 0">
                            <div class="flex justify-between items-center text-teal-600">
                                <span class="font-bold whitespace-nowrap flex items-center gap-1"><el-icon><PriceTag /></el-icon> 免收会员券:</span>
                                <el-switch v-model="isWaiveCoupon" active-text="是" inactive-text="否" inline-prompt @change="handleDiscountChange" />
                            </div>
                            <div v-if="!isWaiveCoupon && (currentMember.coupon || 0) < theoreticalCouponUsed" class="text-[10px] text-red-500 text-right mt-1.5 font-bold animate-pulse leading-tight">
                                ⚠️ 余额不足以抵扣 ￥{{ theoreticalCouponUsed.toFixed(2) }}，请充值或开启免收！
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <div class="w-[65%] flex flex-col border rounded-lg overflow-hidden shadow-sm">
                <div class="bg-red-50 p-4 lg:p-5 border-b border-red-100 flex justify-between items-center text-red-600 shrink-0">
                    <div class="flex flex-col justify-center shrink-0 mr-2">
                        <div class="text-2xl font-black whitespace-nowrap">最终应收</div>
                        <div v-if="currentMember.id && theoreticalCouponUsed > 0 && !isWaiveCoupon" class="text-xs font-bold text-teal-600 mt-1 whitespace-nowrap">
                            (抵扣会员券: ￥{{ theoreticalCouponUsed.toFixed(2) }})
                        </div>
                        <div v-else-if="currentMember.id && theoreticalCouponUsed > 0 && isWaiveCoupon" class="text-xs font-bold text-gray-400 line-through mt-1 whitespace-nowrap">
                            (免扣会员券: ￥{{ theoreticalCouponUsed.toFixed(2) }})
                        </div>
                    </div>
                    <div class="text-right flex-1 flex justify-end items-center overflow-hidden">
                        <span class="font-black tracking-tighter" :class="finalPayAmount >= 100000 ? 'text-4xl' : (finalPayAmount >= 10000 ? 'text-5xl' : 'text-6xl')">
                            ￥{{ finalPayAmount.toFixed(2) }}
                        </span>
                    </div>
                </div>
                <div class="flex-1 bg-white p-4 overflow-y-auto">
                    <div class="text-gray-500 font-bold mb-3 flex justify-between items-center border-b pb-2">
                        <span>组合支付明细</span><span v-if="changeAmount > 0" class="text-green-500 text-sm flex items-center gap-1 font-bold">包含找零</span>
                    </div>

                    <div class="grid gap-3" :class="paymentList.length > 3 ? 'grid-cols-2' : 'grid-cols-1'">
                        <div v-for="(pay, index) in paymentList" :key="pay.code" class="flex flex-col bg-gray-50 p-2 rounded border focus-within:border-blue-400 focus-within:bg-blue-50 transition-colors">
                            <div class="flex items-center gap-3">
                                <div class="w-20 font-bold text-gray-700 text-sm tracking-wider truncate" :title="pay.name">{{ pay.name }}</div>
                                <el-input-number
                                    :model-value="pay.amount"
                                    :min="0"
                                    :max="pay.code.includes('CASH') ? 999999 : (Number(finalPayAmount) || 0)"
                                    :precision="2"
                                    :step="10"
                                    class="flex-1 !h-[45px] !text-2xl font-bold"
                                    :controls="false"
                                    placeholder="0"
                                    @update:model-value="(val) => handlePaymentChange(index, val)"
                                    @focus="handleFocus"
                                />
                            </div>
                            <div v-if="pay.code === 'AGGREGATE' && localPayTagDict && localPayTagDict.length > 0" class="flex flex-wrap gap-2 mt-2 ml-[5.5rem]">
                                <el-tag v-for="tag in localPayTagDict" :key="tag.value" :type="pay.activeTag === tag.value ? 'success' : 'info'" :effect="pay.activeTag === tag.value ? 'dark' : 'plain'" class="cursor-pointer font-bold border-0 shadow-sm transition-all hover:scale-105" @click="pay.activeTag = tag.value">
                                    {{ tag.desc }}
                                </el-tag>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <template #footer>
            <div class="flex justify-between items-center px-4 py-3 bg-gray-100 rounded-b-lg border-t mt-2">
                <div class="text-left shrink-0">
                    <div class="text-gray-600 font-bold text-sm">实收总计: <span class="text-gray-900 font-black text-xl">￥{{ totalPaid.toFixed(2) }}</span></div>
                    <div v-if="changeAmount > 0" class="text-green-600 font-black text-xl mt-1">找零金额: ￥{{ changeAmount.toFixed(2) }}</div>
                    <div v-else-if="unpaidAmount > 0" class="text-red-500 font-black text-xl mt-1">还差金额: ￥{{ unpaidAmount.toFixed(2) }}</div>
                </div>
                <div class="flex gap-3 flex-1 justify-end ml-4">
                    <el-button size="large" class="w-28 font-bold text-base" @click="visible = false">取消(Esc)</el-button>
                    <el-button type="danger" size="large" class="!text-2xl font-black tracking-widest shadow-md px-8"
                        @click="submitOrderAction"
                        :loading="submitLoading"
                        :disabled="isSubmitDisabled">
                        {{ isTrialing ? '计价中...' : '确认收款 [空格]' }}
                    </el-button>
                </div>
            </div>
        </template>
    </el-dialog>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { UserFilled, Ticket, PriceTag } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { usePosStore } from '../hooks/usePosStore'
import { req } from "@/api/index.js"
import dictApi from "@/api/system/dict.js" // 🌟 引入字典 API
import Big from 'big.js'

const props = defineProps({
    modelValue: Boolean,
    payMethodDict: { type: Array, default: () => [] },
    memberLevelDesc: String
})
const emit = defineEmits(['update:modelValue', 'checkout-success', 'closed'])

const visible = computed({
    get: () => props.modelValue,
    set: (val) => emit('update:modelValue', val)
})

const submitLoading = ref(false)
const localPayTagDict = ref([]) // 🌟 纯净的数据源存放点
const stockErrorMessage = ref('')

const {
    cartList, currentMember, isWaiveCoupon, manualDiscount, selectedCouponRule, usedCouponCount, paymentList,
    totalAmount, actualCouponUsed, finalPayAmount, theoreticalCouponUsed, participatingAmount,
    reqId, prepareCheckout, clearAll, submitOrder, runTrial, isTrialing
} = usePosStore();

const isSubmitDisabled = computed(() => {
    return isTrialing.value ||
           unpaidAmount.value > 0 ||
           (!isWaiveCoupon.value && currentMember.value.id && currentMember.value.coupon < theoreticalCouponUsed.value);
})

const handleFocus = (event) => {
    event.target.select();
}

const handleKeyDown = (e) => {
    if (!visible.value) return;
    if (e.key === ' ' || e.code === 'Space') {
        const target = e.target;
        if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA') return;
        e.preventDefault();
        if (!isSubmitDisabled.value && !submitLoading.value) {
            submitOrderAction();
        }
    }
}

// 🌟 组件挂载时自主索要支付子标签字典
onMounted(async () => {
    window.addEventListener('keydown', handleKeyDown);
    try {
        const dictRes = await dictApi.loadDict(["paySubTag"])
        if (dictRes && dictRes.paySubTag) {
            localPayTagDict.value = dictRes.paySubTag
        }
    } catch (e) {}
})

onUnmounted(() => { window.removeEventListener('keydown', handleKeyDown); })

const availableCoupons = computed(() => {
    if (!currentMember.value.id || !Array.isArray(currentMember.value.couponList)) return []
    return currentMember.value.couponList.filter(c => participatingAmount.value >= c.threshold)
})

const maxUsableCoupons = computed(() => {
    if (!currentMember.value.id || !selectedCouponRule.value) return 0;
    const maxByAmount = Math.floor(participatingAmount.value / selectedCouponRule.value.threshold);
    return Math.min(maxByAmount, selectedCouponRule.value.availableCount || 0);
})

const totalPaid = computed(() => paymentList.value.reduce((sum, item) => sum.plus(item.amount || 0), new Big(0)).toNumber())
const unpaidAmount = computed(() => {
    const pay = new Big(finalPayAmount.value || 0);
    const paid = new Big(totalPaid.value || 0);
    const diff = pay.minus(paid);
    return diff.gt(0) ? diff.toNumber() : 0;
})
// 找零计算仅用于页面底部红字展示，提交时不发送给后端
const changeAmount = computed(() => {
    const cashItem = paymentList.value.find(p => p.code.includes('CASH'));
    const cashPaid = new Big(cashItem ? (cashItem.amount || 0) : 0);
    const nonCashPaid = new Big(totalPaid.value).minus(cashPaid);
    const pay = new Big(finalPayAmount.value || 0);
    const remainToPay = pay.minus(nonCashPaid);
    const effectiveRemainToPay = remainToPay.gt(0) ? remainToPay : new Big(0);
    const diff = cashPaid.minus(effectiveRemainToPay);
    return diff.gt(0) ? diff.toNumber() : 0;
})

watch(visible, (newVal) => {
    if (newVal) {
        stockErrorMessage.value = ''
        prepareCheckout();
        usedCouponCount.value = 0;

        // 保留兜底的组合方式（聚合扫码、现金），如果后台传了 payMethodDict 则优先使用后台
        const sourceDict = props.payMethodDict.length > 0 ? props.payMethodDict : [{value: 'AGGREGATE', desc: '聚合扫码'}, {value: 'CASH', desc: '现金支付'}]

        paymentList.value = sourceDict.map(dict => ({
            code: dict.value,
            name: dict.desc,
            amount: 0,
            // 🌟 自动选中从后台拉取到的第一个 paySubTag (动态且安全)
            activeTag: (dict.value === 'AGGREGATE' && localPayTagDict.value.length > 0) ? localPayTagDict.value[0].value : null
        }))
        recalculatePayments();
    }
})

const handleDiscountChange = async () => {
    await runTrial();
    recalculatePayments();
}

const handleCouponRuleChange = async () => {
    usedCouponCount.value = selectedCouponRule.value ? maxUsableCoupons.value : 0;
    await handleDiscountChange();
}

const recalculatePayments = () => {
    if (paymentList.value.length === 0) return;
    const aggIndex = paymentList.value.findIndex(p => p.code.includes('AGGREGATE'));
    const targetIndex = aggIndex >= 0 ? aggIndex : 0;
    paymentList.value.forEach((p, i) => { if (i !== targetIndex) p.amount = 0; });
    paymentList.value[targetIndex].amount = finalPayAmount.value;
}

const handlePaymentChange = (index, val) => {
    let newVal = new Big(val || 0);
    if (paymentList.value[index].code.includes('BALANCE')) {
        const maxBal = new Big(currentMember.value.balance || 0);
        if (newVal.gt(maxBal)) { newVal = maxBal; ElMessage.warning('已限制为最大可用会员余额！'); }
    }
    paymentList.value[index].amount = newVal.toNumber();
    const aggIndex = paymentList.value.findIndex(p => p.code.includes('AGGREGATE'));
    if (aggIndex !== -1 && aggIndex !== index) {
        const otherSum = paymentList.value.reduce((sum, p, i) => i !== aggIndex ? sum.plus(p.amount || 0) : sum, new Big(0));
        const target = new Big(finalPayAmount.value || 0);
        const remain = target.minus(otherSum);
        paymentList.value[aggIndex].amount = remain.gt(0) ? remain.toNumber() : 0;
    }
}

const handleClosed = () => {
    paymentList.value = [];
    manualDiscount.value = 0;
    isWaiveCoupon.value = false;
    selectedCouponRule.value = null;
    runTrial();
    emit('closed');
}

const submitOrderAction = async () => {
    if (unpaidAmount.value > 0) return ElMessage.error(`实付不足 ￥${unpaidAmount.value.toFixed(2)}`);

    const validPayments = paymentList.value
        .filter(p => p.amount > 0)
        .map(p => {
            return {
                payMethodCode: p.code,
                payMethodName: p.name,
                payAmount: p.amount,
                payTag: p.activeTag || null
            };
        });

    const orderDetails = cartList.value.map(item => ({ goodsId: item.id, quantity: Number(item.qty) || 1 }));
    submitLoading.value = true;

    try {
        const payload = {
            reqId: reqId.value,
            member: currentMember.value.id || null,
            usedCouponRuleId: selectedCouponRule.value?.ruleId || null,
            usedCouponCount: selectedCouponRule.value ? usedCouponCount.value : 0,
            waiveCoupon: isWaiveCoupon.value,
            manualDiscountAmount: manualDiscount.value || 0,
            orderDetail: orderDetails,
            payments: validPayments
        };
        const res = await submitOrder(payload, { handledBusinessCodes: [30002] })
        ElMessage.success('收款成功！订单已真实入库！')
        try {
            const orderNoToPrint = (res && res.data && res.data.orderNo) || (res && res.orderNo) || (typeof res === 'string' ? res : null);
            if (orderNoToPrint) {
                req({ url: '/oms-order/hardware/print', method: 'GET', params: { orderNo: orderNoToPrint } }).catch(e=>console.log("硬件打印静默失败:", e));
            }
        } catch(e) { console.log(e) }
        emit('checkout-success', { total: totalAmount.value, paid: totalPaid.value, couponUsed: actualCouponUsed.value })
        visible.value = false;
    } catch (error) {
        console.error("结账异常", error);
        if (error.bizCode === 30002) {
            stockErrorMessage.value = error.message || '库存不足，请调整数量或完成补货后重试。';
        } else if (!error.isGlobalMessageShown) {
            ElMessage.error(error.msg || error.message || '结账失败，后端风控拦截');
        }
    } finally {
        submitLoading.value = false
    }
}
</script>

<style scoped>
:deep(.checkout-dialog .el-dialog__header) { display: none; }
:deep(.checkout-dialog .el-dialog__body) { padding: 0; border-radius: 8px; overflow: hidden; }
</style>
