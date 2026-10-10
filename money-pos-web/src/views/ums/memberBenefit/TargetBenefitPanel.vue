<template>
  <section>
    <el-button link @click="$emit('back')">← 返回权益首页</el-button>
    <div class="my-3 flex flex-wrap items-center justify-between gap-2 text-sm text-gray-500"><span>建立计划不收款；普通销售只有在结账时明确选择后才计入计划。</span><el-button type="primary" @click="createVisible=true">建立升级计划</el-button></div>
    <el-table :data="plans" border>
      <el-table-column prop="brandName" label="品牌" />
      <el-table-column prop="targetTierName" label="升级目标" />
      <el-table-column prop="progressAmount" label="当前进度"><template #default="{ row }">￥{{ row.progressAmount }}</template></el-table-column>
      <el-table-column prop="targetAmount" label="目标金额"><template #default="{ row }">￥{{ row.targetAmount }}</template></el-table-column>
      <el-table-column label="剩余金额"><template #default="{ row }">￥{{ remainingAmount(row) }}</template></el-table-column>
      <el-table-column label="状态"><template #default="{ row }">{{ statusText(row.status) }}</template></el-table-column>
      <el-table-column label="操作" width="100"><template #default="{ row }"><el-button v-if="row.status === 'IN_PROGRESS'" link type="danger" @click="openCancel(row)">取消计划</el-button></template></el-table-column>
    </el-table>
    <el-dialog v-model="createVisible" title="建立会员升级计划" width="520px" append-to-body destroy-on-close>
      <el-form label-width="94px"><el-form-item label="升级目标"><el-select v-model="targetTierCode" class="w-full" placeholder="选择目标"><el-option v-for="tier in enabledTiers" :key="tier.tierCode" :value="tier.tierCode" :label="`${tier.brandName || '品牌'} / ${tier.tierName} / 目标￥${tier.configuredAmount}`"/></el-select></el-form-item><el-form-item label="初始进度"><el-input-number v-model="initialProgress" :min="0" :max="Number(selectedTier?.configuredAmount || 0)" :precision="2" class="w-full"/></el-form-item><el-form-item label="原因"><el-input v-model="reason" maxlength="200" show-word-limit placeholder="可选备注"/></el-form-item></el-form>
      <template #footer><el-button @click="createVisible=false">取消</el-button><el-button type="primary" :disabled="!selectedTier" :loading="submitting" @click="createPlan">确认建立</el-button></template>
    </el-dialog>
    <el-dialog v-model="cancelVisible" title="取消升级计划" width="480px" append-to-body destroy-on-close><el-alert title="仅无后续销售、补差或豁免流水的进行中计划可以取消；取消后保留完整审计记录。" type="warning" :closable="false"/><el-form class="mt-3" label-width="70px"><el-form-item label="取消原因" required><el-input v-model="cancelReason" maxlength="200" placeholder="请说明取消原因"/></el-form-item></el-form><template #footer><el-button @click="cancelVisible=false">返回</el-button><el-button type="danger" :disabled="!cancelReason.trim()" :loading="submitting" @click="cancelPlan">确认取消</el-button></template></el-dialog>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import benefitApi from '@/api/ums/memberBenefit.js'
const props = defineProps({ plans: { type: Array, default: () => [] }, tiers: { type: Array, default: () => [] }, memberId: { type: Number, required: true } })
const emit = defineEmits(['back', 'refresh'])
const createVisible = ref(false), cancelVisible = ref(false), cancellingPlan = ref(null), cancelReason = ref(''), targetTierCode = ref(''), initialProgress = ref(0), reason = ref(''), submitting = ref(false)
const enabledTiers = computed(() => props.tiers.filter(row => row.enabled))
const selectedTier = computed(() => enabledTiers.value.find(row => row.tierCode === targetTierCode.value))
const reqId = () => `MB-TARGET-PLAN-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
const createPlan = async () => { submitting.value = true; try { await benefitApi.createTargetPlan({ memberId: props.memberId, brandId: selectedTier.value.brandId, targetTierCode: selectedTier.value.tierCode, initialProgress: initialProgress.value || 0, reason: reason.value, reqId: reqId() }); ElMessage.success('升级计划已建立'); createVisible.value = false; targetTierCode.value = ''; initialProgress.value = 0; reason.value = ''; emit('refresh') } finally { submitting.value = false } }
const openCancel = (plan) => { cancellingPlan.value = plan; cancelReason.value = ''; cancelVisible.value = true }
const cancelPlan = async () => { if (!cancellingPlan.value || !cancelReason.value.trim()) return; submitting.value = true; try { await benefitApi.cancelTargetPlan({ planId: cancellingPlan.value.planId, reason: cancelReason.value.trim(), reqId: `MB-TARGET-CANCEL-${Date.now()}-${Math.random().toString(36).slice(2, 8)}` }); ElMessage.success('升级计划已取消'); cancelVisible.value = false; cancellingPlan.value = null; emit('refresh') } finally { submitting.value = false } }
const remainingAmount = (plan) => Math.max(0, Number(plan.targetAmount || 0) - Number(plan.progressAmount || 0)).toFixed(2)
const statusText = (status) => ({ IN_PROGRESS: '进行中', PENDING_CONFIRM: '待确认', REVIEW_REQUIRED: '待复核', COMPLETED: '已完成', CANCELLED: '已取消' }[status] || '已结束')
</script>
