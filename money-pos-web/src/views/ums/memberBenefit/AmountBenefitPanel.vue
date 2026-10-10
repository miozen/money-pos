<template>
  <section>
    <el-button link @click="$emit('back')">← 返回权益首页</el-button>
    <div class="my-3 text-sm text-gray-500">每次只选择一份权益包；商品仅可来自该权益包所属品牌。</div>
    <el-table :data="rights" border>
      <el-table-column prop="brandName" label="品牌" />
      <el-table-column prop="tierName" label="权益包" />
      <el-table-column prop="pricingLevelName" label="适用价格档" />
      <el-table-column prop="remainingAmount" label="可用余额"><template #default="{ row }">￥{{ row.remainingAmount }}</template></el-table-column>
      <el-table-column label="状态"><template #default="{ row }">{{ statusText(row.status) }}</template></el-table-column>
    </el-table>
    <div class="mt-3"><el-button type="primary" @click="purchaseVisible=true">购买权益包</el-button></div>
    <el-divider content-position="left">使用权益余额</el-divider>
    <el-form label-width="92px"><el-form-item label="权益包"><el-select v-model="selectedRightId" class="w-full" placeholder="选择一份可用权益包"><el-option v-for="right in availableRights" :key="right.rightId" :value="right.rightId" :label="`${right.brandName || '品牌权益'} / ${right.tierName} / 余额￥${right.remainingAmount}`"/></el-select></el-form-item></el-form>
    <template v-if="selectedRight"><SmartGoodsSelector ref="goodsSelector" mode="pos" placeholder="扫码或搜索同品牌商品" @select="addGoods"/><el-table v-if="items.length" :data="items" border class="mt-3"><el-table-column prop="name" label="商品"/><el-table-column label="数量" width="150"><template #default="{row}"><el-input-number v-model="row.quantity" :min="1" @change="preview=null"/></template></el-table-column><el-table-column width="80"><template #default="{ $index }"><el-button link type="danger" @click="items.splice($index,1);preview=null">移除</el-button></template></el-table-column></el-table><div class="mt-3 flex gap-2"><el-button :disabled="!items.length" @click="loadPreview">预览金额</el-button><el-button type="primary" :disabled="!preview || submitting" @click="confirmVisible=true">确认提货</el-button></div></template>
    <el-descriptions v-if="preview" class="mt-3" :column="2" border><el-descriptions-item label="商品金额">￥{{preview.goodsAmount}}</el-descriptions-item><el-descriptions-item label="权益抵扣">￥{{preview.rightDeductAmount}}</el-descriptions-item><el-descriptions-item label="补差">￥{{preview.supplementAmount}}</el-descriptions-item><el-descriptions-item label="余额后">￥{{preview.remainingAmountAfter}}</el-descriptions-item></el-descriptions>
    <el-dialog v-model="confirmVisible" title="确认权益提货" width="520px" append-to-body destroy-on-close><BenefitPaymentEditor :required-amount="Number(preview?.supplementAmount || 0)" @update:payments="payments=$event"/><template #footer><el-button @click="confirmVisible=false">取消</el-button><el-button type="primary" :loading="submitting" @click="pickup">确认提货</el-button></template></el-dialog>
    <el-dialog v-model="purchaseVisible" title="购买品牌权益包" width="520px" append-to-body destroy-on-close><el-select v-model="purchaseTierCode" class="w-full" placeholder="选择权益包"><el-option v-for="tier in purchasableTiers" :key="tier.packageCode" :value="tier.packageCode" :label="`${tier.packageName} / 售价￥${tier.purchaseAmount} / 权益￥${tier.benefitAmount}`"/></el-select><BenefitPaymentEditor class="mt-3" :required-amount="Number(selectedTier?.purchaseAmount || 0)" @update:payments="purchasePayments=$event"/><template #footer><el-button @click="purchaseVisible=false">取消</el-button><el-button type="primary" :disabled="!selectedTier" :loading="submitting" @click="purchasePackage">确认购买</el-button></template></el-dialog>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import SmartGoodsSelector from '@/components/common/SmartGoodsSelector.vue'
import BenefitPaymentEditor from './BenefitPaymentEditor.vue'
import benefitApi from '@/api/ums/memberBenefit.js'
import { req } from '@/api/index.js'
const props = defineProps({ rights: { type: Array, default: () => [] }, tiers: { type: Array, default: () => [] }, memberId: { type: Number, required: true } })
const emit = defineEmits(['back', 'refresh'])
const selectedRightId=ref(null), items=ref([]), preview=ref(null), payments=ref([]), confirmVisible=ref(false), purchaseVisible=ref(false), purchaseTierCode=ref(''), purchasePayments=ref([]), submitting=ref(false), packages=ref([])
const availableRights=computed(()=>props.rights.filter(row=>row.status==='ACTIVE'&&Number(row.remainingAmount)>0)); const selectedRight=computed(()=>availableRights.value.find(row=>row.rightId===selectedRightId.value))
const purchasableTiers=computed(()=>packages.value.filter(row=>row.enabled)); const selectedTier=computed(()=>purchasableTiers.value.find(row=>row.packageCode===purchaseTierCode.value))
const reqId=(name)=>`MB-${name}-${Date.now()}-${Math.random().toString(36).slice(2,8)}`
watch(selectedRightId,()=>{items.value=[];preview.value=null;payments.value=[]})
const addGoods=(goods)=>{ if(String(goods.brandId)!==String(selectedRight.value.brandId)) return ElMessage.warning('该商品不属于所选权益包品牌'); const item=items.value.find(row=>row.goodsId===goods.id); if(item)item.quantity+=1; else items.value.push({goodsId:goods.id,name:goods.name,quantity:1}); preview.value=null }
const handleBarcode=async(barcode)=>{ if(!selectedRight.value)return ElMessage.warning('请先选择一份权益包'); const res=await req({url:'/pos/goods',method:'GET',params:{barcode}}); const goods=res.data||[]; if(goods.length===1)addGoods(goods[0]); else ElMessage.warning(goods.length?'匹配多个商品，请搜索选择':'未找到该商品条码') }
const payload=()=>({memberId:props.memberId,amountRightId:selectedRightId.value,lines:items.value.map(row=>({goodsId:row.goodsId,quantity:row.quantity}))})
const loadPreview=async()=>{ if(!selectedRightId.value||!items.value.length)return; const res=await benefitApi.amountPickupPreview(payload()); preview.value=res.data||res }
const pickup=async()=>{ submitting.value=true; try { const res=await benefitApi.amountPickup({...payload(),reqId:reqId('AMOUNT-PICKUP'),payments:payments.value}); const pickupNo=(res.data||res||{}).pickupNo; if(pickupNo) benefitApi.amountPickupReceipt(pickupNo).catch(()=>{}); ElMessage.success('提货成功'); items.value=[];preview.value=null;confirmVisible.value=false;emit('refresh') } finally {submitting.value=false} }
const purchasePackage=async()=>{ submitting.value=true; try { await benefitApi.amountPurchase({memberId:props.memberId,brandId:selectedTier.value.brandId,tierCode:selectedTier.value.packageCode,reqId:reqId('AMOUNT-PURCHASE'),payments:purchasePayments.value}); purchaseVisible.value=false; purchaseTierCode.value=''; purchasePayments.value=[]; ElMessage.success('权益包购买成功'); emit('refresh') } finally {submitting.value=false} }
benefitApi.amountPackages().then(r=>{packages.value=r.data||r||[]}).catch(()=>{packages.value=[]})
defineExpose({ handleBarcode })
const statusText = (status) => ({ ACTIVE: '可使用', COMPLETED: '已完成', REFUNDED: '已退款' }[status] || '已结束')
</script>
