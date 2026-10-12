<template>
  <section class="mt-5 border-t border-gray-200 pt-3">
    <div class="mb-2 flex items-center justify-between gap-2"><span class="font-medium text-gray-700">办理流水</span><el-button link type="primary" :loading="loading" @click="toggle">{{ limit === 10 ? '查看 20 条' : '显示 10 条' }}</el-button></div>
    <el-table :data="records" size="small" border stripe max-height="280" v-loading="loading" empty-text="暂无办理流水">
      <el-table-column prop="createTime" label="时间" width="155" />
      <el-table-column prop="subjectName" label="主体" min-width="120" show-overflow-tooltip />
      <el-table-column prop="actionName" label="业务" min-width="115" />
      <el-table-column label="变动" width="96" align="right"><template #default="{ row }"><span :class="Number(row.delta) >= 0 ? 'text-green-600' : 'text-red-500'">{{ signed(row.delta) }}{{ row.unit }}</span></template></el-table-column>
      <el-table-column label="变动后" width="96" align="right"><template #default="{ row }">{{ value(row.afterValue) }}{{ row.unit }}</template></el-table-column>
      <el-table-column label="关联单据" min-width="135"><template #default="{ row }"><el-link v-if="clickable(row)" type="primary" :underline="false" class="font-mono text-xs" @click="open(row)">{{ row.referenceNo }}</el-link><span v-else class="text-gray-300">—</span></template></el-table-column>
      <el-table-column prop="operatorName" label="操作员" width="95" show-overflow-tooltip />
    </el-table>
    <OrderDetailModal v-model="orderVisible" :order-no="detailNo" />
    <el-dialog v-model="pickupVisible" title="会员提货单" width="560px" append-to-body><el-descriptions v-if="pickup" :column="2" :label-width="88" border><el-descriptions-item label="提货单号">{{ pickup.pickupNo }}</el-descriptions-item><el-descriptions-item label="会员姓名">{{ pickup.memberName || '—' }}</el-descriptions-item><el-descriptions-item label="手机号码">{{ pickup.memberPhone || '—' }}</el-descriptions-item><el-descriptions-item label="办理时间">{{ pickup.pickupTime }}</el-descriptions-item><el-descriptions-item label="操作员">{{ pickup.operatorName }}</el-descriptions-item></el-descriptions><el-table v-if="pickup" class="mt-3" :data="pickup.lines || []" border><el-table-column prop="goodsName" label="商品" min-width="200"/><el-table-column prop="goodsBarcode" label="条码" min-width="140"/><el-table-column prop="quantity" label="数量" width="70" align="center"/></el-table></el-dialog>
    <el-dialog v-model="receiptVisible" title="会员权益凭证" width="620px" append-to-body><el-descriptions v-if="receipt" :column="2" border><el-descriptions-item label="凭证号">{{ receipt.receiptNo }}</el-descriptions-item><el-descriptions-item label="状态">{{ receipt.status }}</el-descriptions-item><el-descriptions-item label="凭证类型">{{ receiptType(receipt) }}</el-descriptions-item><el-descriptions-item label="办理时间">{{ receipt.createTime }}</el-descriptions-item><el-descriptions-item label="业务金额">￥{{ value(receipt.totalAmount) }}</el-descriptions-item><el-descriptions-item label="权益抵扣">￥{{ value(receipt.rightDeductAmount) }}</el-descriptions-item><el-descriptions-item label="补差/入账">￥{{ value(receipt.supplementAmount) }}</el-descriptions-item><el-descriptions-item label="原因">{{ receipt.reason || '—' }}</el-descriptions-item></el-descriptions><el-table v-if="receipt" class="mt-3" :data="receipt.payments || []" size="small" border empty-text="无支付明细"><el-table-column prop="payMethodName" label="支付方式"/><el-table-column label="渠道"><template #default="{row}">{{ payTagText(row.payTag) }}</template></el-table-column><el-table-column label="金额" align="right"><template #default="{row}">￥{{ value(row.amount) }}</template></el-table-column></el-table></el-dialog>
  </section>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import benefitApi from '@/api/ums/memberBenefit.js'
import OrderDetailModal from '@/components/OrderDetailModal.vue'
import dictApi from '@/api/system/dict.js'
const props = defineProps({ memberId: { type: Number, required: true }, benefitType: { type: String, required: true }, refreshKey: { type: Number, default: 0 } })
const records = ref([]), loading = ref(false), limit = ref(10), orderVisible = ref(false), detailNo = ref(''), pickupVisible = ref(false), pickup = ref(null), receiptVisible = ref(false), receipt = ref(null), payTags = ref([])
const load = async () => { if (!props.memberId) return; loading.value = true; try { const res = await benefitApi.operationHistory({ memberId: props.memberId, benefitType: props.benefitType, limit: limit.value }); records.value = (res.data || res || {}).records || [] } finally { loading.value = false } }
const toggle = () => { limit.value = limit.value === 10 ? 20 : 10; load() }
const clickable = row => Boolean(row?.referenceNo && ['ORDER', 'QUANTITY_PICKUP', 'AMOUNT_PICKUP', 'AMOUNT_RECEIPT', 'TARGET_RECEIPT'].includes(row.detailTarget))
const open = async (row) => { detailNo.value = row.referenceNo; if (row.detailTarget === 'ORDER') { orderVisible.value = true; return }; try { if (row.detailTarget === 'QUANTITY_PICKUP' || row.detailTarget === 'AMOUNT_PICKUP') { const res = row.detailTarget === 'QUANTITY_PICKUP' ? await benefitApi.quantityPickupReceiptPreview(row.referenceNo) : await benefitApi.amountPickupReceiptPreview(row.referenceNo); pickup.value = res.data || res; pickupVisible.value = true; return }; const res = await benefitApi.receiptDetail({ receiptKind: row.detailTarget === 'AMOUNT_RECEIPT' ? 'AMOUNT' : 'TARGET', receiptNo: row.referenceNo }); receipt.value = res.data || res; receiptVisible.value = true } catch (_) { ElMessage.error('获取关联单据失败，请稍后重试') } }
const signed = amount => `${Number(amount || 0) > 0 ? '+' : ''}${value(amount)}`
const value = amount => Number(amount || 0).toFixed(2)
const receiptType = row => ({ AMOUNT_PACKAGE_PURCHASE: '办理金额权益包', AMOUNT_PICKUP_SUPPLEMENT: '金额权益提货补差', SUPPLEMENT: '升级补差', WAIVER: '升级豁免' }[row.receiptType] || row.receiptType)
const payTagText = tag => { if (!tag) return '—'; const row = payTags.value.find(item => item?.value === tag || item?.dictValue === tag); return row?.desc || row?.cnDesc || row?.dictLabel || tag }
dictApi.loadDict(['paySubTag']).then(result => { payTags.value = result?.paySubTag || [] }).catch(() => { payTags.value = [] })
watch(() => [props.memberId, props.benefitType, props.refreshKey], load, { immediate: true })
defineExpose({ load, toggle })
</script>
