<template>
  <section class="h-full flex flex-col gap-4 overflow-hidden">
    <div class="flex flex-wrap items-center gap-3 rounded-lg border border-gray-200 bg-gray-50 p-3 shrink-0">
      <div class="min-w-56 flex-1">
        <div v-if="selectedMember" class="flex flex-col gap-0.5">
          <span class="font-bold text-gray-800">{{ selectedMember.name || selectedMember.nickname || `会员 #${selectedMember.id}` }}</span>
          <span class="text-sm text-gray-500">{{ selectedMember.phone || '已选择会员' }}</span>
        </div>
        <span v-else class="text-sm text-gray-500">请先搜索并选择要办理权益的会员</span>
      </div>
      <MemberSmartSearch v-model="memberSearchId" class="w-full sm:w-96" size="default" placeholder="手机号 / 姓名搜索并切换会员" @select="selectMember" @clear="clearMember" />
    </div>

    <el-alert v-if="!selectedMember" title="请选择会员后查看和办理品牌权益。此选择不会绑定或修改当前 POS 销售会员。" type="info" :closable="false" />

    <template v-else>
      <div v-loading="loading" class="flex-1 min-h-0 overflow-auto pr-1">
        <el-row :gutter="16" class="mb-4">
          <el-col :md="8" :xs="24"><el-card shadow="never"><template #header>数量权益</template><div class="text-2xl font-bold">{{ overview.quantityRights.length }}</div><div class="text-gray-400 text-sm">可追溯的延迟提货权益</div></el-card></el-col>
          <el-col :md="8" :xs="24"><el-card shadow="never"><template #header>金额权益余额</template><div class="text-2xl font-bold">￥{{ amountBalance }}</div><div class="text-gray-400 text-sm">按冻结价格档提货</div></el-card></el-col>
          <el-col :md="8" :xs="24"><el-card shadow="never"><template #header>TARGET 计划</template><div class="text-2xl font-bold">{{ overview.targetPlans.length }}</div><div class="text-gray-400 text-sm">达标后仍需人工确认</div></el-card></el-col>
        </el-row>

        <el-tabs v-model="activeTab">
          <el-tab-pane label="权益与档位" name="rights">
            <div class="flex gap-2 mb-3"><el-button type="primary" @click="openQuantityPurchase">购买数量权益</el-button><el-button @click="purchaseVisible=true">购买金额权益包</el-button><el-button @click="quantityVisible=true">数量权益提货</el-button><el-button @click="amountPickupVisible=true">金额权益提货</el-button></div>
            <div class="mb-3"><SmartGoodsSelector mode="pos" placeholder="搜索或扫描提货商品" @select="addPickupItem" /></div>
            <el-table v-if="pickupItems.length" :data="pickupItems" border size="small" class="mb-5"><el-table-column prop="name" label="待提商品"/><el-table-column prop="barcode" label="条码"/><el-table-column label="数量" width="160"><template #default="{row}"><el-input-number v-model="row.quantity" :min="1" /></template></el-table-column><el-table-column width="80"><template #default="{row,$index}"><el-button link type="danger" @click="pickupItems.splice($index,1)">移除</el-button></template></el-table-column></el-table>
            <el-table :data="overview.tiers" border size="small" class="mb-5"><el-table-column prop="brandId" label="品牌"/><el-table-column prop="tierName" label="档位"/><el-table-column prop="configuredAmount" label="配置金额"><template #default="{row}">￥{{ row.configuredAmount }}</template></el-table-column><el-table-column prop="pricingLevelCode" label="价格档"/><el-table-column prop="enabled" label="状态"><template #default="{row}"><el-tag :type="row.enabled?'success':'info'">{{row.enabled?'启用':'停用'}}</el-tag></template></el-table-column></el-table>
            <el-table :data="overview.quantityRights" border size="small" class="mb-5"><el-table-column prop="rightId" label="权益 ID"/><el-table-column prop="brandId" label="品牌"/><el-table-column prop="goodsId" label="商品 ID"/><el-table-column prop="grantedQuantity" label="发放"/><el-table-column prop="pickedQuantity" label="已提"/><el-table-column prop="remainingQuantity" label="剩余"/><el-table-column prop="status" label="状态"/></el-table>
            <el-table :data="overview.amountRights" border size="small"><el-table-column prop="rightId" label="权益 ID"/><el-table-column prop="brandId" label="品牌"/><el-table-column prop="tierName" label="档位"/><el-table-column prop="grantedAmount" label="发放"><template #default="{row}">￥{{row.grantedAmount}}</template></el-table-column><el-table-column prop="remainingAmount" label="剩余"><template #default="{row}">￥{{row.remainingAmount}}</template></el-table-column><el-table-column prop="status" label="状态"/></el-table>
          </el-tab-pane>
          <el-tab-pane label="TARGET" name="target">
            <div class="flex gap-2 mb-3"><el-button type="primary" :disabled="!selectedPlan" @click="targetSettleVisible=true">TARGET 销售结算</el-button><el-button :disabled="!selectedPlan" @click="targetVisible=true">补差 / 豁免</el-button><el-button :disabled="!selectedPlan" @click="confirmTarget">人工确认升级</el-button><el-button :disabled="!selectedPlan" @click="loadLogs">查看流水</el-button></div>
            <el-table :data="overview.targetPlans" border highlight-current-row @current-change="selectedPlan=$event"><el-table-column prop="planId" label="计划 ID"/><el-table-column prop="brandId" label="品牌"/><el-table-column prop="targetTierName" label="目标档位"/><el-table-column prop="progressAmount" label="当前进度"/><el-table-column prop="targetAmount" label="目标金额"/><el-table-column prop="status" label="状态"/></el-table>
            <el-table v-if="targetLogs.length" :data="targetLogs" border size="small" class="mt-4"><el-table-column prop="createTime" label="时间"/><el-table-column prop="action" label="动作"/><el-table-column prop="amountDelta" label="变动"/><el-table-column prop="afterAmount" label="变动后"/><el-table-column prop="sourceNo" label="来源单号"/><el-table-column prop="reason" label="原因"/></el-table>
          </el-tab-pane>
          <el-tab-pane label="交易历史" name="history"><el-table :data="history" border><el-table-column prop="createTime" label="时间"/><el-table-column prop="recordType" label="类型"/><el-table-column prop="referenceNo" label="单号"/><el-table-column prop="amount" label="金额"/><el-table-column prop="status" label="状态"/><el-table-column prop="sourceNo" label="关联"/><el-table-column prop="refundNo" label="退款单"/><el-table-column label="操作" width="100"><template #default="{row}"><el-button v-if="row.recordType==='AMOUNT_PICKUP'&&row.status!=='REFUNDED'" link type="danger" @click="refundPickup(row)">整笔退款</el-button></template></el-table-column></el-table></el-tab-pane>
        </el-tabs>
      </div>
    </template>

    <el-dialog v-model="quantityPurchaseVisible" title="购买数量权益（延迟提货）" width="520px" append-to-body><el-alert title="请先在工作台主页面搜索或扫描商品；购买不扣实体库存。" type="info" :closable="false" /><PaymentFields v-model:method="quantityPurchase.method" v-model:amount="quantityPurchase.amount" /><template #footer><el-button @click="quantityPurchaseVisible=false">取消</el-button><el-button type="primary" @click="submitQuantityPurchase">确认购买</el-button></template></el-dialog>
    <el-dialog v-model="purchaseVisible" title="购买金额权益包" width="440px" append-to-body><el-form label-width="100px"><el-form-item label="档位"><el-select v-model="purchase.tierCode" class="w-full"><el-option v-for="tier in overview.tiers.filter(t=>t.enabled)" :key="tier.tierCode" :label="`${tier.brandId} / ${tier.tierName} / ￥${tier.configuredAmount}`" :value="tier.tierCode" /></el-select></el-form-item><PaymentFields v-model:method="purchase.method" v-model:amount="purchase.amount" /></el-form><template #footer><el-button @click="purchaseVisible=false">取消</el-button><el-button type="primary" @click="submitPurchase">确认购买</el-button></template></el-dialog>
    <el-dialog v-model="quantityVisible" title="数量权益提货" width="440px" append-to-body><el-form label-width="100px"><el-form-item label="权益"><el-select v-model="quantity.rightId" class="w-full"><el-option v-for="right in overview.quantityRights.filter(r=>r.status==='ACTIVE'&&r.remainingQuantity>0)" :key="right.rightId" :label="`#${right.rightId} 商品 ${right.goodsId}（余 ${right.remainingQuantity}）`" :value="right.rightId" /></el-select></el-form-item><el-form-item label="提货数量"><el-input-number v-model="quantity.quantity" :min="1" /></el-form-item></el-form><template #footer><el-button @click="quantityVisible=false">取消</el-button><el-button type="primary" @click="submitQuantity">确认提货</el-button></template></el-dialog>
    <el-dialog v-model="amountPickupVisible" title="金额权益提货" width="440px" append-to-body><el-form label-width="100px"><el-form-item label="金额权益"><el-select v-model="amountPickup.rightId" class="w-full"><el-option v-for="right in overview.amountRights.filter(r=>r.status==='ACTIVE'&&r.remainingAmount>0)" :key="right.rightId" :label="`#${right.rightId} ${right.tierName}（余￥${right.remainingAmount}）`" :value="right.rightId" /></el-select></el-form-item><el-alert title="请先在工作台权益页搜索或扫描商品，确认后按待提商品清单提货。" type="info" :closable="false" /><PaymentFields v-model:method="amountPickup.method" v-model:amount="amountPickup.amount" /></el-form><template #footer><el-button @click="amountPickupVisible=false">取消</el-button><el-button type="primary" @click="submitAmountPickup">确认提货</el-button></template></el-dialog>
    <el-dialog v-model="targetSettleVisible" title="TARGET 销售结算" width="520px" append-to-body><el-alert title="请先在工作台主页面搜索或扫描同品牌商品；该操作仅走已冻结的 TARGET 结算路径。" type="info" :closable="false" /><PaymentFields v-model:method="targetSettle.method" v-model:amount="targetSettle.amount" /><template #footer><el-button @click="targetSettleVisible=false">取消</el-button><el-button type="primary" @click="submitTargetSettle">确认结算</el-button></template></el-dialog>
    <el-dialog v-model="targetVisible" title="TARGET 进度调整" width="440px" append-to-body><el-form label-width="100px"><el-form-item label="方式"><el-radio-group v-model="target.action"><el-radio label="supplement">补差</el-radio><el-radio label="waive">人工豁免</el-radio></el-radio-group></el-form-item><el-form-item label="金额"><el-input-number v-model="target.amount" :min="0.01" :precision="2" /></el-form-item><el-form-item label="原因"><el-input v-model="target.reason" /></el-form-item><PaymentFields v-if="target.action==='supplement'" v-model:method="target.method" v-model:amount="target.amount" /></el-form><template #footer><el-button @click="targetVisible=false">取消</el-button><el-button type="primary" @click="submitTarget">确认</el-button></template></el-dialog>
  </section>
</template>

<script setup>
import { computed, defineComponent, h, ref, watch } from 'vue'
import { ElFormItem, ElInputNumber, ElSelect, ElOption } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import MemberSmartSearch from '@/components/common/MemberSmartSearch.vue'
import SmartGoodsSelector from '@/components/common/SmartGoodsSelector.vue'
import { req } from '@/api/index.js'
import benefitApi from '@/api/ums/memberBenefit.js'

const props = defineProps({ initialMember: { type: Object, default: null } })
const selectedMember = ref(null)
const memberSearchId = ref(null)
const brandId = ref('')
const loading = ref(false)
const activeTab = ref('rights')
const selectedPlan = ref(null)
const targetLogs = ref([])
const history = ref([])
const pickupItems = ref([])
const overview = ref({ tiers: [], quantityRights: [], amountRights: [], targetPlans: [] })
const amountBalance = computed(() => overview.value.amountRights.reduce((sum, row) => sum + Number(row.remainingAmount || 0), 0).toFixed(2))
const purchaseVisible=ref(false), quantityVisible=ref(false), amountPickupVisible=ref(false), targetVisible=ref(false), quantityPurchaseVisible=ref(false), targetSettleVisible=ref(false)
const purchase=ref({tierCode:'',method:'CASH',amount:0}), quantity=ref({rightId:null,quantity:1}), amountPickup=ref({rightId:null,goodsId:null,quantity:1,method:'CASH',amount:0}), target=ref({action:'supplement',amount:0,reason:'',method:'CASH'}), quantityPurchase=ref({method:'CASH',amount:0}), targetSettle=ref({method:'CASH',amount:0})
const reqId = (name) => `MB-${name}-${Date.now()}-${Math.random().toString(36).slice(2,8)}`
const payment = (method, amount) => amount > 0 ? [{ payMethodCode: method, payMethodName: method, payAmount: Number(amount) }] : []

const resetWorkspace = (member) => {
  selectedMember.value = member?.id ? { ...member } : null
  memberSearchId.value = null
  overview.value = { tiers: [], quantityRights: [], amountRights: [], targetPlans: [] }
  selectedPlan.value = null
  targetLogs.value = []
  history.value = []
}
const selectMember = (member) => { selectedMember.value = { ...member } }
const clearMember = () => { selectedMember.value = null }
const addPickupItem = (goods) => {
  const item = pickupItems.value.find(row => String(row.goodsId) === String(goods.id))
  if (item) item.quantity += 1
  else pickupItems.value.push({ goodsId: goods.id, name: goods.name, barcode: goods.barcode, quantity: 1 })
}
const handleBarcode = async (barcode) => {
  const res = await req({ url: '/pos/goods', method: 'GET', params: { barcode } })
  const goods = res.data || []
  if (goods.length === 1) addPickupItem(goods[0])
  else ElMessage.warning(goods.length ? '匹配多个商品，请在工作台内选择' : '未找到该商品条码')
}
const load = async () => {
  if (!selectedMember.value?.id) return
  loading.value = true
  try {
    const res = await benefitApi.overview({ memberId: selectedMember.value.id, brandId: brandId.value || undefined })
    overview.value = res.data || res
    selectedPlan.value = null
    targetLogs.value = []
  } finally { loading.value = false }
}
const loadHistory = async () => {
  if (!selectedMember.value?.id) return
  const res = await benefitApi.tradeHistory(selectedMember.value.id)
  history.value = (res.data || res).records || []
  activeTab.value = 'history'
}
const loadLogs = async () => { if (selectedPlan.value) { const res = await benefitApi.targetLogs(selectedPlan.value.planId); targetLogs.value = res.data || res } }
const refresh = () => { load(); loadHistory() }
const pickupLines = () => pickupItems.value.map(({ goodsId, quantity }) => ({ goodsId, quantity: Number(quantity) }))
const openQuantityPurchase = () => { if (!pickupItems.value.length) return ElMessage.warning('请先搜索或扫描要购买的商品'); quantityPurchaseVisible.value = true }
const submitQuantityPurchase = async () => { if (!pickupItems.value.length) return ElMessage.warning('请先搜索或扫描要购买的商品'); await benefitApi.quantityPurchase({ member: selectedMember.value.id, reqId: reqId('QUANTITY-BUY'), orderDetail: pickupLines(), payments: payment(quantityPurchase.value.method, quantityPurchase.value.amount) }); quantityPurchaseVisible.value=false; ElMessage.success('数量权益已购买，后续提货才扣库存'); refresh() }
const submitPurchase = async () => { const tier=overview.value.tiers.find(t=>t.tierCode===purchase.value.tierCode); if(!tier) return ElMessage.warning('请选择档位'); await benefitApi.amountPurchase({memberId:selectedMember.value.id,brandId:tier.brandId,tierCode:tier.tierCode,reqId:reqId('AMOUNT'),payments:payment(purchase.value.method,tier.configuredAmount)}); purchaseVisible.value=false; ElMessage.success('金额权益包已办理'); refresh() }
const submitQuantity = async () => { if(!quantity.value.rightId) return ElMessage.warning('请选择权益'); await benefitApi.quantityPickup({memberId:selectedMember.value.id,reqId:reqId('QUANTITY'),lines:[{rightId:quantity.value.rightId,quantity:quantity.value.quantity}]}); quantityVisible.value=false; ElMessage.success('提货成功'); refresh() }
const submitAmountPickup = async () => { if(!amountPickup.value.rightId||!pickupItems.value.length) return ElMessage.warning('请选择权益和待提商品'); await benefitApi.amountPickup({memberId:selectedMember.value.id,amountRightId:amountPickup.value.rightId,reqId:reqId('PICKUP'),lines:pickupItems.value.map(({goodsId,quantity})=>({goodsId,quantity})),payments:payment(amountPickup.value.method,amountPickup.value.amount)}); amountPickupVisible.value=false; ElMessage.success('提货成功'); pickupItems.value=[]; refresh() }
const submitTargetSettle = async () => { if (!selectedPlan.value || !pickupItems.value.length) return ElMessage.warning('请选择计划并搜索或扫描商品'); await benefitApi.targetSettle({ targetPlanId: selectedPlan.value.planId, settle: { member: selectedMember.value.id, reqId: reqId('TARGET-SETTLE'), orderDetail: pickupLines(), payments: payment(targetSettle.value.method, targetSettle.value.amount) } }); targetSettleVisible.value=false; pickupItems.value=[]; ElMessage.success('TARGET 销售结算成功'); refresh() }
const submitTarget = async () => { if(!selectedPlan.value) return; const data={targetPlanId:selectedPlan.value.planId,reqId:reqId('TARGET'),amount:target.value.amount,reason:target.value.reason,payments:target.value.action==='supplement'?payment(target.value.method,target.value.amount):[]}; await (target.value.action==='supplement'?benefitApi.targetSupplement(data):benefitApi.targetWaive(data)); targetVisible.value=false; ElMessage.success('TARGET 进度已更新'); load(); loadLogs(); loadHistory() }
const confirmTarget = async () => { if(!selectedPlan.value) return; const {value:reason}=await ElMessageBox.prompt('填写确认原因（可选）','人工确认升级',{inputPlaceholder:'确认原因',required:false}); await benefitApi.targetConfirm({targetPlanId:selectedPlan.value.planId,reqId:reqId('CONFIRM'),reason}); ElMessage.success('已提交人工确认'); load(); loadLogs() }
const refundPickup = async (row) => { await ElMessageBox.confirm(`仅可整笔退款 ${row.referenceNo}，确认继续？`, '金额权益提货退款', { type: 'warning' }); await benefitApi.amountPickupRefund({ pickupNo: row.referenceNo, reqId: reqId('AMOUNT-REFUND') }); ElMessage.success('整笔退款已提交'); refresh() }
watch(() => props.initialMember, resetWorkspace, { immediate: true })
watch(() => selectedMember.value?.id, (id) => { if (id) { load(); loadHistory() } }, { immediate: true })
watch(brandId, () => { if (selectedMember.value?.id) load() })
const PaymentFields = defineComponent({ props:{method:String,amount:Number}, emits:['update:method','update:amount'], setup(props,{emit}) { return () => h('div',[h(ElFormItem,{label:'支付方式'},()=>h(ElSelect,{modelValue:props.method,'onUpdate:modelValue':v=>emit('update:method',v)},()=>['CASH','BALANCE'].map(v=>h(ElOption,{label:v,value:v})))),h(ElFormItem,{label:'补差/实收'},()=>h(ElInputNumber,{modelValue:props.amount,min:0,precision:2,'onUpdate:modelValue':v=>emit('update:amount',v)}))]); } })
defineExpose({ handleBarcode })
</script>
