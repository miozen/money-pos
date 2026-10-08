<template>
  <section>
    <el-button link @click="$emit('back')">← 返回权益首页</el-button>
    <div class="my-3 text-sm text-gray-500">寄存与多商品提货将在下一步以独立清单办理，不会影响普通 POS 购物车。</div>
    <el-table :data="rights" border>
      <el-table-column prop="goodsName" label="可提商品" />
      <el-table-column prop="grantedQuantity" label="寄存件数" />
      <el-table-column prop="pickedQuantity" label="已提件数" />
      <el-table-column prop="remainingQuantity" label="剩余件数" />
      <el-table-column label="状态"><template #default="{ row }">{{ statusText(row.status) }}</template></el-table-column>
    </el-table>
  </section>
</template>

<script setup>
defineProps({ rights: { type: Array, default: () => [] } })
defineEmits(['back'])
const statusText = (status) => ({ ACTIVE: '可使用', COMPLETED: '已完成', REFUNDED: '已退款' }[status] || '已结束')
</script>
