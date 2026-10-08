<template>
  <section>
    <el-button link @click="$emit('back')">← 返回权益首页</el-button>
    <div class="my-3 text-sm text-gray-500">建立计划和销售累计将在后续步骤接入；普通销售不会自动累计。</div>
    <el-table :data="plans" border>
      <el-table-column prop="brandName" label="品牌" />
      <el-table-column prop="targetTierName" label="升级目标" />
      <el-table-column prop="progressAmount" label="当前进度"><template #default="{ row }">￥{{ row.progressAmount }}</template></el-table-column>
      <el-table-column prop="targetAmount" label="目标金额"><template #default="{ row }">￥{{ row.targetAmount }}</template></el-table-column>
      <el-table-column label="剩余金额"><template #default="{ row }">￥{{ remainingAmount(row) }}</template></el-table-column>
      <el-table-column label="状态"><template #default="{ row }">{{ statusText(row.status) }}</template></el-table-column>
    </el-table>
  </section>
</template>

<script setup>
defineProps({ plans: { type: Array, default: () => [] } })
defineEmits(['back'])
const remainingAmount = (plan) => Math.max(0, Number(plan.targetAmount || 0) - Number(plan.progressAmount || 0)).toFixed(2)
const statusText = (status) => ({ IN_PROGRESS: '进行中', PENDING_CONFIRM: '待确认', REVIEW_REQUIRED: '待复核', COMPLETED: '已完成' }[status] || '已结束')
</script>
