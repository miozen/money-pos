<template>
  <div class="p-4 sm:p-6 lg:p-8 bg-gray-50 min-h-full">
    <div class="max-w-5xl mx-auto flex flex-col gap-6">
      <div class="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3">
        <div>
          <h1 class="text-2xl font-black text-gray-800">Omnia Cloud 会员同步</h1>
          <p class="text-sm text-gray-500 mt-2">云端同步设置仅保存在本机收银机；保存、启停和重新同步仅可在本机后台操作。</p>
        </div>
        <el-button :loading="loading" @click="loadStatus">刷新状态</el-button>
      </div>

      <el-alert v-if="status" :type="alertType" :closable="false" show-icon>
        <template #title>{{ status.enabled ? (status.configurationComplete ? '自动同步已开启' : '自动同步已开启，但设置未完成') : '自动同步未开启' }}</template>
        <template #default>保存以下设置后立即生效；开启同步前请填写云端服务地址、门店标识和两项密钥。</template>
      </el-alert>

      <el-card shadow="never">
        <template #header><span class="font-bold">云端同步设置</span></template>
        <el-form :model="config" label-width="132px" class="max-w-3xl" @submit.prevent>
          <el-form-item label="云端服务地址"><el-input v-model="config.endpoint" placeholder="https://…/internal/v1/member-snapshots:batch" /></el-form-item>
          <el-form-item label="门店标识"><el-input v-model="config.storeId" placeholder="例如 store-001" /></el-form-item>
          <el-form-item label="同步密钥">
            <el-input v-model="config.signingSecretBase64" :type="signingSecretVisible ? 'text' : 'password'" :placeholder="config.signingSecretConfigured ? '已设置；留空则保持不变' : '请输入同步密钥'">
              <template #suffix><el-icon class="cursor-pointer" :class="{ 'is-loading': revealingSecret === 'signing' }" @click.stop="toggleSecret('signing')"><component :is="revealingSecret === 'signing' ? Loading : (signingSecretVisible ? Hide : View)" /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item label="会员检索密钥">
            <el-input v-model="config.searchHmacSecretBase64" :type="searchHmacSecretVisible ? 'text' : 'password'" :placeholder="config.searchHmacSecretConfigured ? '已设置；留空则保持不变' : '请输入会员检索密钥'">
              <template #suffix><el-icon class="cursor-pointer" :class="{ 'is-loading': revealingSecret === 'searchHmac' }" @click.stop="toggleSecret('searchHmac')"><component :is="revealingSecret === 'searchHmac' ? Loading : (searchHmacSecretVisible ? Hide : View)" /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item label="同步开关"><el-switch v-model="config.enabled" active-text="启用" inactive-text="关闭" /></el-form-item>
          <el-form-item>
            <div class="flex flex-wrap gap-2">
              <el-button type="primary" :loading="saving" @click="saveConfig">保存设置</el-button>
              <el-button :loading="testing" :disabled="!status?.configurationComplete" @click="testConnection">测试云端连接</el-button>
              <el-button :loading="dispatching" :disabled="!status?.enabled" @click="dispatchNow">立即同步</el-button>
              <el-popconfirm title="将把全部会员资料重新同步到云端（包括已删除记录）；不会修改本机会员资料。" confirm-button-text="开始重新同步" @confirm="beginRepair">
                <template #reference><el-button :loading="repairing" :disabled="!status?.enabled">重新同步全部会员</el-button></template>
              </el-popconfirm>
            </div>
          </el-form-item>
        </el-form>
      </el-card>

      <div v-if="status" class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <el-card shadow="never"><div class="text-sm text-gray-500">同步设置</div><div class="mt-2 font-bold text-lg">{{ status.configurationComplete ? '已完成' : '未完成' }}</div></el-card>
        <el-card shadow="never"><div class="text-sm text-gray-500">云端门店标识</div><div class="mt-2 font-bold text-lg">{{ status.storeId || '未设置' }}</div></el-card>
        <el-card shadow="never"><div class="text-sm text-gray-500">首次同步</div><div class="mt-2 font-bold text-lg">{{ bootstrapStatusLabel(status.bootstrapStatus) }}</div></el-card>
      </div>

      <el-card v-if="status" shadow="never">
        <template #header><span class="font-bold">同步队列</span></template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="等待同步">{{ status.pending }}</el-descriptions-item>
          <el-descriptions-item label="同步失败（将自动重试）">{{ status.failed }}</el-descriptions-item>
          <el-descriptions-item label="正在同步">{{ status.processing }}</el-descriptions-item>
          <el-descriptions-item label="已同步">{{ status.sent }}</el-descriptions-item>
          <el-descriptions-item label="最近同步时间" :span="2">{{ formatTime(status.lastSentAt) }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card v-if="status" shadow="never">
        <template #header><span class="font-bold">首次同步进度</span></template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="状态">{{ bootstrapStatusLabel(status.bootstrapStatus) }}</el-descriptions-item>
          <el-descriptions-item label="已加入同步队列">{{ status.bootstrapTotalEnqueued ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ formatTime(status.bootstrapCompletedAt) }}</el-descriptions-item>
        </el-descriptions>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Hide, Loading, View } from '@element-plus/icons-vue'
import memberCloudSyncApi from '@/api/ums/memberCloudSync.js'

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const dispatching = ref(false)
const repairing = ref(false)
const revealingSecret = ref('')
const signingSecretVisible = ref(false)
const searchHmacSecretVisible = ref(false)
const status = ref(null)
const config = ref({ endpoint: '', storeId: 'store-01', signingSecretBase64: '', searchHmacSecretBase64: '', signingSecretConfigured: false, searchHmacSecretConfigured: false, enabled: false })
const alertType = computed(() => status.value?.enabled && !status.value?.configurationComplete ? 'error' : status.value?.enabled ? 'success' : 'warning')

async function loadStatus() {
  loading.value = true
  try {
    const response = await memberCloudSyncApi.status()
    status.value = response.data
  } catch (error) {
    ElMessage.error('读取云端同步状态失败')
  } finally {
    loading.value = false
  }
}

async function loadConfig() {
  const response = await memberCloudSyncApi.config()
  config.value = { ...config.value, ...response.data, signingSecretBase64: '', searchHmacSecretBase64: '' }
  signingSecretVisible.value = false
  searchHmacSecretVisible.value = false
}

async function saveConfig() {
  saving.value = true
  try {
    const response = await memberCloudSyncApi.saveConfig(config.value)
    config.value = { ...config.value, ...response.data, signingSecretBase64: '', searchHmacSecretBase64: '' }
    signingSecretVisible.value = false
    searchHmacSecretVisible.value = false
    ElMessage.success('云端同步设置已保存')
    await loadStatus()
  } catch (error) {
    ElMessage.error('保存云端同步设置失败，请检查填写内容后重试')
  } finally {
    saving.value = false
  }
}

async function toggleSecret(kind) {
  const visible = kind === 'signing' ? signingSecretVisible : searchHmacSecretVisible
  const field = kind === 'signing' ? 'signingSecretBase64' : 'searchHmacSecretBase64'
  if (visible.value) {
    config.value = { ...config.value, [field]: '' }
    visible.value = false
    return
  }
  revealingSecret.value = kind
  try {
    const response = await memberCloudSyncApi.revealSecrets()
    config.value = { ...config.value, [field]: response.data[field] }
    visible.value = true
  } catch (error) {
    ElMessage.error('读取已保存密钥失败，请稍后重试')
  } finally {
    revealingSecret.value = ''
  }
}

async function dispatchNow() {
  dispatching.value = true
  try {
    await memberCloudSyncApi.dispatch()
    ElMessage.success('已执行一轮同步')
    await loadStatus()
  } catch (error) {
    ElMessage.error('立即同步失败，请稍后重试')
  } finally {
    dispatching.value = false
  }
}

async function testConnection() {
  testing.value = true
  try {
    await memberCloudSyncApi.testConnection()
    ElMessage.success('云端服务连接正常，未同步会员资料')
  } catch (error) {
    ElMessage.error('云端服务连接失败，请检查服务地址和同步密钥')
  } finally {
    testing.value = false
  }
}

async function beginRepair() {
  repairing.value = true
  try {
    await memberCloudSyncApi.repair()
    ElMessage.success('已开始重新同步全部会员资料，系统将在后台自动完成')
    await loadStatus()
  } catch (error) {
    ElMessage.error('重新同步未能启动，请稍后重试')
  } finally {
    repairing.value = false
  }
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ') : '—'
}

function bootstrapStatusLabel(value) {
  if (value === 'COMPLETED') return '已完成'
  if (value === 'REPAIRING') return '正在重新同步'
  if (value === 'PENDING' || value === 'RUNNING') return '进行中'
  return '等待开始'
}

onMounted(async () => {
  try { await Promise.all([loadStatus(), loadConfig()]) } catch (error) { ElMessage.error('读取云端同步设置失败') }
})
</script>
