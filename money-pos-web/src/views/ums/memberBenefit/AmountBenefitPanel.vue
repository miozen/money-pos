<template>
  <section>
    <el-button link @click="$emit('back')">← 返回权益首页</el-button>
    <div class="my-3 text-sm text-gray-500">购买和使用余额将在下一步以一份权益包、一张局部清单办理。</div>
    <el-table :data="rights" border>
      <el-table-column prop="brandName" label="品牌" />
      <el-table-column prop="tierName" label="权益包" />
      <el-table-column prop="pricingLevelName" label="适用价格档" />
      <el-table-column prop="remainingAmount" label="可用余额"><template #default="{ row }">￥{{ row.remainingAmount }}</template></el-table-column>
      <el-table-column label="状态"><template #default="{ row }">{{ statusText(row.status) }}</template></el-table-column>
    </el-table>
  </section>
</template>

<script setup>
defineProps({ rights: { type: Array, default: () => [] } })
defineEmits(['back'])
const statusText = (status) => ({ ACTIVE: '可使用', COMPLETED: '已完成', REFUNDED: '已退款' }[status] || '已结束')
</script>
