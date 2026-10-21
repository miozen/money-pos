<template>
  <div class="p-4 sm:p-6 lg:p-8 bg-gray-50 min-h-full">
    <div class="max-w-5xl mx-auto flex flex-col gap-6">
      <div class="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3">
        <div>
          <h1 class="text-2xl font-black text-gray-800">Omnia Cloud 会员同步</h1>
          <p class="text-sm text-gray-500 mt-2">本页只显示本机状态；不会显示或保存云端密钥，也不能在此开启同步。</p>
        </div>
        <el-button :loading="loading" @click="loadStatus">刷新状态</el-button>
      </div>

      <el-alert v-if="status" :type="alertType" :closable="false" show-icon>
        <template #title>{{ status.enabled ? (status.configurationComplete ? '同步调度已启用' : '同步已启用，但外部配置不完整') : '同步当前未启用' }}</template>
        <template #default>配置文件应位于 <code>%ProgramData%\\VanaPOS\\config\\omnia-cloud.yml</code>。修改后需重启万象收银才会生效。</template>
      </el-alert>

      <div v-if="status" class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <el-card shadow="never"><div class="text-sm text-gray-500">发布配置</div><div class="mt-2 font-bold text-lg">{{ status.configurationComplete ? '完整' : '未完整配置' }}</div></el-card>
        <el-card shadow="never"><div class="text-sm text-gray-500">门店 / 租户</div><div class="mt-2 font-bold text-lg">{{ status.storeId || '未设置' }} / {{ status.tenantId ?? '未设置' }}</div></el-card>
        <el-card shadow="never"><div class="text-sm text-gray-500">首次回填</div><div class="mt-2 font-bold text-lg">{{ status.initialBackfillEnabled ? '已授权执行' : '未启用' }}</div></el-card>
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
import memberCloudSyncApi from '@/api/ums/memberCloudSync.js'

const loading = ref(false)
const status = ref(null)
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

function formatTime(value) {
  return value ? String(value).replace('T', ' ') : '—'
}

onMounted(loadStatus)
</script>
