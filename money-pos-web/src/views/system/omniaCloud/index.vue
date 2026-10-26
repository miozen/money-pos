<template>
  <div class="p-4 sm:p-6 lg:p-8 bg-gray-50 min-h-full">
    <div class="max-w-5xl mx-auto flex flex-col gap-6">
      <div class="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3">
        <div>
          <h1 class="text-2xl font-black text-gray-800">Omnia Cloud 会员同步</h1>
          <p class="text-sm text-gray-500 mt-2">配置只保存在本机 POS。保存、启停和数据修复仅允许从本机后台执行。</p>
        </div>
        <el-button :loading="loading" @click="loadStatus">刷新状态</el-button>
      </div>

      <el-alert v-if="status" :type="alertType" :closable="false" show-icon>
        <template #title>{{ status.enabled ? (status.configurationComplete ? '同步调度已启用' : '同步已启用，但配置不完整') : '同步当前未启用' }}</template>
        <template #default>填写并保存下方配置后立即生效；启用同步前必须提供有效地址、门店和两个密钥。</template>
      </el-alert>

      <el-card shadow="never">
        <template #header><span class="font-bold">本机连接配置</span></template>
        <el-form :model="config" label-width="132px" class="max-w-3xl" @submit.prevent>
          <el-form-item label="Worker 地址"><el-input v-model="config.endpoint" placeholder="https://…/internal/v1/member-snapshots:batch" /></el-form-item>
          <el-form-item label="门店编号"><el-input v-model="config.storeId" placeholder="例如 store-001" /></el-form-item>
          <el-form-item label="签名密钥（密码）">
            <el-input v-model="config.signingSecretBase64" :type="signingSecretVisible ? 'text' : 'password'" :placeholder="config.signingSecretConfigured ? '已设置；留空则保持不变' : 'Base64 密钥'">
              <template #suffix><el-icon class="cursor-pointer" :class="{ 'is-loading': revealingSecret === 'signing' }" @click.stop="toggleSecret('signing')"><component :is="revealingSecret === 'signing' ? Loading : (signingSecretVisible ? Hide : View)" /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item label="检索 HMAC 密钥">
            <el-input v-model="config.searchHmacSecretBase64" :type="searchHmacSecretVisible ? 'text' : 'password'" :placeholder="config.searchHmacSecretConfigured ? '已设置；留空则保持不变' : 'Base64 密钥'">
              <template #suffix><el-icon class="cursor-pointer" :class="{ 'is-loading': revealingSecret === 'searchHmac' }" @click.stop="toggleSecret('searchHmac')"><component :is="revealingSecret === 'searchHmac' ? Loading : (searchHmacSecretVisible ? Hide : View)" /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item label="同步开关"><el-switch v-model="config.enabled" active-text="启用" inactive-text="关闭" /></el-form-item>
          <el-form-item>
            <div class="flex flex-wrap gap-2">
              <el-button type="primary" :loading="saving" @click="saveConfig">保存本机配置</el-button>
              <el-button :loading="testing" :disabled="!status?.configurationComplete" @click="testConnection">测试连接</el-button>
              <el-button :loading="dispatching" :disabled="!status?.enabled" @click="dispatchNow">立即同步</el-button>
              <el-popconfirm title="将按当前会员状态重新投递所有快照（含已删除会员）。不会修改本地会员数据。" confirm-button-text="开始修复" @confirm="beginRepair">
                <template #reference><el-button :loading="repairing" :disabled="!status?.enabled">数据修复</el-button></template>
              </el-popconfirm>
            </div>
          </el-form-item>
        </el-form>
      </el-card>

      <div v-if="status" class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <el-card shadow="never"><div class="text-sm text-gray-500">发布配置</div><div class="mt-2 font-bold text-lg">{{ status.configurationComplete ? '完整' : '未完整配置' }}</div></el-card>
        <el-card shadow="never"><div class="text-sm text-gray-500">门店分区</div><div class="mt-2 font-bold text-lg">{{ status.storeId || '未设置' }}</div></el-card>
        <el-card shadow="never"><div class="text-sm text-gray-500">首次回填</div><div class="mt-2 font-bold text-lg">{{ status.bootstrapStatus === 'COMPLETED' ? '已完成' : '自动执行中/待执行' }}</div></el-card>
      </div>

      <el-card v-if="status" shadow="never">
        <template #header><span class="font-bold">本地待发箱</span></template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="待发送">{{ status.pending }}</el-descriptions-item>
          <el-descriptions-item label="失败待重试">{{ status.failed }}</el-descriptions-item>
          <el-descriptions-item label="发送处理中">{{ status.processing }}</el-descriptions-item>
          <el-descriptions-item label="已确认发送">{{ status.sent }}</el-descriptions-item>
          <el-descriptions-item label="最近确认时间" :span="2">{{ formatTime(status.lastSentAt) }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card v-if="status" shadow="never">
        <template #header><span class="font-bold">首次会员回填检查点</span></template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="状态">{{ status.bootstrapStatus }}</el-descriptions-item>
          <el-descriptions-item label="已入待发箱">{{ status.bootstrapTotalEnqueued ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="会员游标">{{ status.bootstrapCursorMemberId ?? '—' }}</el-descriptions-item>
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
    ElMessage.error('读取 Omnia Cloud 同步状态失败')
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
    ElMessage.success('Omnia Cloud 本机配置已保存')
    await loadStatus()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '保存 Omnia Cloud 配置失败')
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
    ElMessage.error(error?.response?.data?.message || '读取已保存密钥失败')
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
    ElMessage.error(error?.response?.data?.message || '立即同步失败')
  } finally {
    dispatching.value = false
  }
}

async function testConnection() {
  testing.value = true
  try {
    await memberCloudSyncApi.testConnection()
    ElMessage.success('Worker 签名验证和 D1 连接正常；未写入会员快照')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '连接测试失败')
  } finally {
    testing.value = false
  }
}

async function beginRepair() {
  repairing.value = true
  try {
    await memberCloudSyncApi.repair()
    ElMessage.success('数据修复已开始，会员快照将按后台同步节奏重新投递')
    await loadStatus()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '启动数据修复失败')
  } finally {
    repairing.value = false
  }
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ') : '—'
}

onMounted(async () => {
  try { await Promise.all([loadStatus(), loadConfig()]) } catch (error) { ElMessage.error('读取 Omnia Cloud 配置失败') }
})
</script>
