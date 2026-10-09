<template>
  <section>
    <el-button link @click="$emit('back')">← 返回权益首页</el-button>
    <div class="my-3 text-sm text-gray-500">寄存和提货都使用本窗口独立清单，不会影响普通 POS 购物车。</div>
    <div class="mb-3"><SmartGoodsSelector ref="goodsSelector" mode="pos" placeholder="扫码或搜索寄存商品" @select="addGoods" /></div>
    <el-table v-if="purchaseItems.length" :data="purchaseItems" border class="mb-3"><el-table-column prop="name" label="寄存商品"/><el-table-column label="数量" width="160"><template #default="{row}"><el-input-number v-model="row.quantity" :min="1" @change="loadTrial"/></template></el-table-column><el-table-column label="试算单价"><template #default="{row}">￥{{ unitPrice(row.goodsId) }}</template></el-table-column><el-table-column label="小计"><template #default="{row}">￥{{ lineAmount(row.goodsId) }}</template></el-table-column><el-table-column width="80"><template #default="{ $index }"><el-button link type="danger" @click="purchaseItems.splice($index, 1);loadTrial()">移除</el-button></template></el-table-column></el-table>
    <div class="mb-4"><el-button type="primary" :disabled="!purchaseItems.length || submitting" @click="purchaseVisible=true">办理寄存</el-button></div>
    <el-table :data="rights" border>
      <el-table-column prop="goodsName" label="可提商品" />
      <el-table-column prop="grantedQuantity" label="寄存件数" />
      <el-table-column prop="pickedQuantity" label="已提件数" />
      <el-table-column prop="remainingQuantity" label="剩余件数" />
      <el-table-column label="本次提货" width="150"><template #default="{ row }"><el-input-number v-model="row.pickupQuantity" :min="0" :max="row.remainingQuantity" /></template></el-table-column>
      <el-table-column label="状态"><template #default="{ row }">{{ statusText(row.status) }}</template></el-table-column>
    </el-table>
    <div class="mt-3"><el-button :disabled="!rights.some(row => Number(row.pickupQuantity) > 0) || submitting" @click="pickup">确认勾选提货</el-button></div>
    <el-dialog v-model="purchaseVisible" title="确认商品寄存" width="520px" append-to-body destroy-on-close><el-alert title="寄存确认收入，但不会扣减实体库存。" type="info" :closable="false"/><el-form class="mt-3" label-width="90px"><el-form-item label="整单优惠"><el-input-number v-model="manualDiscount" :min="0" :max="Number(pricing?.memberAmount||0)" :precision="2" @change="loadTrial"/></el-form-item></el-form><el-descriptions v-if="pricing" :column="2" border><el-descriptions-item label="商品金额">￥{{pricing.memberAmount}}</el-descriptions-item><el-descriptions-item label="整单优惠">￥{{pricing.manualDeduct}}</el-descriptions-item><el-descriptions-item label="最终应收">￥{{pricing.finalPayAmount}}</el-descriptions-item><el-descriptions-item label="会员优惠">￥{{pricing.privilegeAmount}}</el-descriptions-item></el-descriptions><BenefitPaymentEditor class="mt-3" :required-amount="Number(pricing?.finalPayAmount||0)" :member-balance="Number(memberBalance||0)" @update:payments="payments=$event"/><template #footer><el-button @click="purchaseVisible=false">取消</el-button><el-button type="primary" :disabled="!pricing||unpaid>0" :loading="submitting" @click="purchase">确认寄存</el-button></template></el-dialog>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import SmartGoodsSelector from '@/components/common/SmartGoodsSelector.vue'
import BenefitPaymentEditor from './BenefitPaymentEditor.vue'
import benefitApi from '@/api/ums/memberBenefit.js'
import { req } from '@/api/index.js'
const props = defineProps({ rights: { type: Array, default: () => [] }, memberId: { type: Number, required: true }, memberBalance: { type: Number, default: 0 } })
const emit = defineEmits(['back', 'refresh'])
const goodsSelector = ref(null), purchaseItems = ref([]), payments = ref([]), purchaseVisible = ref(false), submitting = ref(false), pricing = ref(null), manualDiscount = ref(0)
const memberBalance = computed(() => props.memberBalance || 0)
const paid = computed(() => payments.value.reduce((sum, item) => sum + Number(item.payAmount || 0), 0))
const unpaid = computed(() => Math.max(0, Number(pricing.value?.finalPayAmount || 0) - paid.value))
const reqId = (name) => `MB-${name}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
const trialPayload=()=>({member:props.memberId,manualDiscountAmount:manualDiscount.value||0,waiveCoupon:false,items:purchaseItems.value.map(row=>({goodsId:row.goodsId,quantity:row.quantity}))})
const loadTrial=async()=>{if(!purchaseItems.value.length){pricing.value=null;return}const res=await benefitApi.quantityTrial(trialPayload());pricing.value=res.data||res}
const addGoods = (goods) => { const item = purchaseItems.value.find(row => row.goodsId === goods.id); if (item) item.quantity += 1; else purchaseItems.value.push({ goodsId: goods.id, name: goods.name, quantity: 1 }); loadTrial() }
const handleBarcode = async (barcode) => { const res = await req({ url: '/pos/goods', method: 'GET', params: { barcode } }); const goods = res.data || []; if (goods.length === 1) addGoods(goods[0]); else ElMessage.warning(goods.length ? '匹配多个商品，请搜索选择' : '未找到该商品条码') }
const unitPrice=id=>Number(pricing.value?.items?.find(x=>x.goodsId===id)?.unitRealPrice||0).toFixed(2);const lineAmount=id=>Number(pricing.value?.items?.find(x=>x.goodsId===id)?.subTotalMember||0).toFixed(2)
const purchase = async () => { if(unpaid.value>0)return ElMessage.error(`实付不足 ￥${unpaid.value.toFixed(2)}`); submitting.value = true; try { await benefitApi.quantityPurchase({ member: props.memberId, reqId: reqId('QUANTITY-BUY'), manualDiscountAmount:manualDiscount.value||0, waiveCoupon:false, usedCouponCount:0, orderDetail: purchaseItems.value.map(row => ({ goodsId: row.goodsId, quantity: row.quantity })), payments: payments.value }); purchaseItems.value=[];pricing.value=null;manualDiscount.value=0; purchaseVisible.value=false; ElMessage.success('商品已寄存'); emit('refresh') } finally { submitting.value=false } }
const pickup = async () => { const lines = props.rights.filter(row => Number(row.pickupQuantity) > 0).map(row => ({ rightId: row.rightId, quantity: Number(row.pickupQuantity) })); if (!lines.length) return ElMessage.warning('请填写至少一项提货数量'); submitting.value=true; try { await benefitApi.quantityPickup({ memberId: props.memberId, reqId: reqId('QUANTITY-PICKUP'), lines }); ElMessage.success('提货成功'); emit('refresh') } finally { submitting.value=false } }
defineExpose({ handleBarcode })
const statusText = (status) => ({ ACTIVE: '可使用', COMPLETED: '已完成', REFUNDED: '已退款' }[status] || '已结束')
</script>
