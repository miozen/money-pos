<template>
  <section class="h-full flex flex-col gap-4 overflow-hidden">
    <div class="flex flex-wrap items-center gap-3 rounded-lg border border-gray-200 bg-gray-50 p-3 shrink-0">
      <div class="min-w-56 flex-1">
        <div v-if="selectedMember" class="flex flex-col gap-0.5">
          <span class="font-bold text-gray-800">{{ selectedMember.name || selectedMember.nickname || '已选择会员' }}</span>
          <span class="text-sm text-gray-500">{{ selectedMember.phone || '已选择会员' }}</span>
        </div>
        <span v-else class="text-sm text-gray-500">请先搜索并选择要办理权益的会员</span>
      </div>
      <MemberSmartSearch v-model="memberSearchId" class="w-full sm:w-96" size="default" placeholder="手机号 / 姓名搜索并切换会员" @select="selectMember" @clear="clearMember" />
    </div>

    <el-alert v-if="!selectedMember" title="请选择会员后查看和办理品牌权益。此选择不会绑定或修改当前 POS 销售会员。" type="info" :closable="false" />

    <template v-else>
      <div v-loading="loading" class="flex-1 min-h-0 overflow-auto pr-1">
        <div v-if="activeTab === 'home'" class="grid gap-4 md:grid-cols-3">
          <el-card shadow="hover" class="cursor-pointer" @click="activeTab = 'quantity'"><template #header><b>商品寄存</b></template><div class="text-2xl font-bold">{{ overview.quantityRights.length }} 项</div><div class="mt-2 text-sm text-gray-500">查看可提商品和剩余件数，办理寄存或提货。</div></el-card>
          <el-card shadow="hover" class="cursor-pointer" @click="activeTab = 'amount'"><template #header><b>品牌权益包</b></template><div class="text-2xl font-bold">￥{{ amountBalance }}</div><div class="mt-2 text-sm text-gray-500">选择品牌权益包，按已发放的价格档使用余额。</div></el-card>
          <el-card shadow="hover" class="cursor-pointer" @click="activeTab = 'target'"><template #header><b>会员升级计划</b></template><div class="text-2xl font-bold">{{ overview.targetPlans.length }} 个</div><div class="mt-2 text-sm text-gray-500">查看目标、当前进度和剩余金额。</div></el-card>
        </div>

        <QuantityBenefitPanel v-else-if="activeTab === 'quantity'" ref="quantityPanel" :rights="overview.quantityRights" :member-id="selectedMember.id" @back="activeTab = 'home'" @refresh="load" />
        <AmountBenefitPanel v-else-if="activeTab === 'amount'" ref="amountPanel" :rights="overview.amountRights" :tiers="overview.tiers" :member-id="selectedMember.id" @back="activeTab = 'home'" @refresh="load" />
        <TargetBenefitPanel v-else :plans="overview.targetPlans" @back="activeTab = 'home'" />
      </div>
    </template>

  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import MemberSmartSearch from '@/components/common/MemberSmartSearch.vue'
import benefitApi from '@/api/ums/memberBenefit.js'
import QuantityBenefitPanel from './QuantityBenefitPanel.vue'
import AmountBenefitPanel from './AmountBenefitPanel.vue'
import TargetBenefitPanel from './TargetBenefitPanel.vue'

const props = defineProps({ initialMember: { type: Object, default: null } })
const selectedMember = ref(null)
const memberSearchId = ref(null)
const loading = ref(false)
const activeTab = ref('home')
const quantityPanel = ref(null)
const amountPanel = ref(null)
const overview = ref({ tiers: [], quantityRights: [], amountRights: [], targetPlans: [] })
const amountBalance = computed(() => overview.value.amountRights.reduce((sum, row) => sum + Number(row.remainingAmount || 0), 0).toFixed(2))

const resetWorkspace = (member) => {
  selectedMember.value = member?.id ? { ...member } : null
  memberSearchId.value = null
  overview.value = { tiers: [], quantityRights: [], amountRights: [], targetPlans: [] }
  activeTab.value = 'home'
}
const selectMember = (member) => { selectedMember.value = { ...member } }
const clearMember = () => { selectedMember.value = null }
const load = async () => {
  if (!selectedMember.value?.id) return
  loading.value = true
  try {
    const res = await benefitApi.overview({ memberId: selectedMember.value.id })
    overview.value = res.data || res
  } finally { loading.value = false }
}
watch(() => props.initialMember, resetWorkspace, { immediate: true })
watch(() => selectedMember.value?.id, (id) => { if (id) load() }, { immediate: true })
const handleBarcode = (barcode) => { if (activeTab.value === 'quantity') return quantityPanel.value?.handleBarcode(barcode); if (activeTab.value === 'amount') return amountPanel.value?.handleBarcode(barcode) }
defineExpose({ handleBarcode })
</script>
