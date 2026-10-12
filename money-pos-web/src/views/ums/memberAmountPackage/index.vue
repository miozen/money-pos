<template>
  <PageWrapper>
    <div class="mb-4 flex justify-between"><span class="text-lg font-bold">金额权益包管理</span><el-button type="primary" @click="open()">新增权益包</el-button></div>
    <el-alert title="停售只影响后续购买；已发放权益继续按其冻结名称、余额和价格档使用。" type="info" :closable="false" class="mb-4" />
    <el-table :data="rows" border v-loading="loading">
      <el-table-column prop="packageName" label="权益包" /><el-table-column prop="packageCode" label="代码" />
      <el-table-column label="品牌"><template #default="{ row }">{{ brandName(row.brandId) }}</template></el-table-column>
      <el-table-column prop="purchaseAmount" label="售价" /><el-table-column prop="benefitAmount" label="权益额" />
      <el-table-column label="价格档"><template #default="{ row }">{{ pricingLevelName(row.pricingLevelCode) }}</template></el-table-column>
      <el-table-column label="状态"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column label="操作"><template #default="{ row }"><el-button link @click="open(row)">编辑</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></template></el-table-column>
    </el-table>
    <el-dialog v-model="visible" :title="form.id ? '编辑权益包' : '新增权益包'" width="520px" destroy-on-close>
      <el-form label-width="104px">
        <el-form-item label="品牌"><el-select v-model="form.brandId" filterable class="w-full" @change="changeBrand"><el-option v-for="brand in brands" :key="brand.value" :label="brand.label" :value="String(brand.value)" /></el-select></el-form-item>
        <el-form-item label="代码"><el-input v-model="form.packageCode" /></el-form-item><el-form-item label="名称"><el-input v-model="form.packageName" /></el-form-item>
        <el-form-item label="售价与权益额"><div class="amount-fields"><el-input-number v-model="form.purchaseAmount" :min="0" :precision="2" placeholder="售价" /><el-input-number v-model="form.benefitAmount" :min="0.01" :precision="2" placeholder="权益额" /></div></el-form-item>
        <el-form-item label="价格档"><el-select v-model="form.pricingLevelCode" class="w-full" :disabled="!form.brandId"><el-option v-for="level in levels" :key="level.value" :label="level.desc" :value="level.value" :disabled="level.disabled" /></el-select></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortNo" :min="0" /></el-form-item><el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item><el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="visible = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </PageWrapper>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageWrapper from '@/components/PageWrapper.vue'
import brandApi from '@/api/gms/brand.js'
import dictApi from '@/api/system/dict.js'
import tierApi from '@/api/ums/memberBenefitTier.js'
import api from '@/api/ums/memberBenefit.js'

const rows = ref([]), brands = ref([]), memberTypes = ref([]), levels = ref([]), loading = ref(false), visible = ref(false), form = ref({})
const brandName = id => brands.value.find(brand => String(brand.value) === String(id))?.label || `已失效品牌（${id}）`
const pricingLevelName = code => memberTypes.value.find(level => level.value === code)?.desc || `已失效价格档（${code}）`
const load = async () => { loading.value = true; try { const result = await api.amountPackages(); rows.value = result.data || result || [] } finally { loading.value = false } }
const loadLevels = async () => {
  if (!form.value.brandId) { levels.value = []; return }
  const result = await tierApi.pricing(form.value.brandId)
  const codes = result.data?.levelCodes || result.levelCodes || []
  const available = memberTypes.value.filter(level => codes.includes(level.value))
  if (form.value.pricingLevelCode && !available.some(level => level.value === form.value.pricingLevelCode)) available.push({ value: form.value.pricingLevelCode, desc: `已失效价格档（${form.value.pricingLevelCode}）`, disabled: true })
  levels.value = available
}
const changeBrand = async () => { form.value.pricingLevelCode = ''; await loadLevels() }
const open = async row => { form.value = row ? { ...row } : { brandId: '', packageCode: '', packageName: '', purchaseAmount: 0, benefitAmount: 0, pricingLevelCode: '', enabled: true, sortNo: 0, remark: '' }; await loadLevels(); visible.value = true }
const save = async () => { await api.saveAmountPackage(form.value); ElMessage.success('权益包已保存'); visible.value = false; await load() }
const remove = async row => { await ElMessageBox.confirm(`删除权益包 ${row.packageName}？`, '确认删除', { type: 'warning' }); await api.deleteAmountPackage(row.id); ElMessage.success('权益包已删除'); await load() }
onMounted(async () => { const [brandResult, dict] = await Promise.all([brandApi.getSelect(), dictApi.loadDict(['memberType'])]); brands.value = brandResult.data || brandResult || []; memberTypes.value = (dict.memberType || []).filter(level => level.value !== 'MEMBER'); await load() })
</script>

<style scoped>
.amount-fields { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 12px; width: 100%; }
.amount-fields :deep(.el-input-number) { width: 100%; }
</style>
