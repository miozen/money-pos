<template>
    <el-table
        :data="enrichedCartList"
        height="100%"
        stripe
        border
        :header-cell-style="{ background: '#e0e7ff', color: '#3730a3', fontSize: '15px', fontWeight: 'bold' }"
        :row-class-name="tableRowClassName"
        @row-click="handleRowClick"
    >
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="barcode" label="条码" width="160" />

        <el-table-column label="商品名称" min-width="250">
            <template #default="{ row }">
                <div>
                    <span class="font-bold mr-2">{{ row.name }}</span>
                    <el-tag v-if="row.isCombo === 1" type="warning" size="small" effect="dark" class="mr-1">套餐</el-tag>
                    <el-tag v-if="row.isDiscountParticipable === 1" type="danger" size="small" effect="plain">满减</el-tag>
                    <div v-if="isStockInsufficient(row)" class="stock-warning mt-1 flex items-center gap-1 text-xs font-bold">
                        <el-icon><WarningFilled /></el-icon>
                        {{ stockWarningText(row) }}
                    </div>
                </div>
            </template>
        </el-table-column>

        <el-table-column label="单价" width="120" align="right">
            <template #default="{ row }">￥{{ row.salePrice?.toFixed(2) }}</template>
        </el-table-column>

        <el-table-column label="会员价" width="120" align="right">
            <template #default="{ row }">
                <span v-if="getLevelCode(row.brandId)" class="text-blue-600 font-bold">
                    ￥{{ row.displayPrice?.toFixed(2) }}
                </span>
                <span v-else class="text-gray-400">-</span>
            </template>
        </el-table-column>

        <el-table-column label="会员券" width="120" align="right">
            <template #default="{ row }">
                <span v-if="getLevelCode(row.brandId) && row.displayCouponDeduct > 0" class="text-green-600 font-bold">
                    ￥{{ (row.displayCouponDeduct / row.qty).toFixed(2) }}
                </span>
                <span v-else class="text-gray-400">-</span>
            </template>
        </el-table-column>

        <el-table-column label="数量" width="160" align="center">
            <template #default="{ row, $index }">
                <el-input-number
                    :model-value="row.qty"
                    :min="1" :max="9999" size="small"
                    :class="[
                        '!w-28 transition-all duration-200',
                        $index === activeItemIndex ? 'ring-2 ring-blue-600 ring-offset-2 rounded shadow-md' : ''
                    ]"
                    @change="(val) => handleQtyChange($index, val)"
                    @focus="handleFocus"
                />
            </template>
        </el-table-column>

        <el-table-column label="小计" width="160" align="right">
            <template #default="{ row }">
                <span
                    class="text-red-600 font-bold text-lg transition-opacity duration-300"
                    :class="{ 'opacity-40': row.isPending }"
                >
                    ￥{{ row.displaySubtotal?.toFixed(2) }}
                </span>
            </template>
        </el-table-column>

        <el-table-column label="库存" width="100" align="center">
            <template #default="{ row }">
                <span :class="{'text-red-600 font-bold': row.qty > (row.stock || 0), 'text-gray-600': row.qty <= (row.stock || 0)}">
                    {{ row.stock !== undefined ? row.stock : '-' }}
                </span>
            </template>
        </el-table-column>

        <el-table-column label="操作" width="150" align="center" fixed="right">
            <template #default="{ row, $index }">
                <div class="flex items-center justify-center gap-1">
                    <el-button v-if="isStockInsufficient(row)" type="warning" link size="small" @click.stop="emit('restock', row)">补货</el-button>
                    <el-button type="danger" icon="Delete" circle plain @click.stop="removeItem($index)" />
                </div>
            </template>
        </el-table-column>
    </el-table>
</template>

<script setup>
import { watch, nextTick } from 'vue'
import { WarningFilled } from '@element-plus/icons-vue'
import { usePosStore } from '../hooks/usePosStore'

const { cartList, enrichedCartList, currentMember, removeItem, runTrial, activeItemIndex } = usePosStore()
const emit = defineEmits(['restock'])

const isStockInsufficient = (row) => Number(row.qty || 0) > Number(row.stock ?? 0)

const stockWarningText = (row) => {
    const stock = Number(row.stock ?? 0)
    return stock <= 0
        ? `库存为 0，已选 ${row.qty}`
        : `库存不足：现有 ${stock}，已选 ${row.qty}`
}

// 🌟 核心逻辑：当输入框获得焦点时，自动全选内容
const handleFocus = (event) => {
    // 这里的 event.target 就是底层的 input 元素，执行 select() 即可实现全选
    event.target.select();
}

// 🌟 自动滚屏黑科技，焦点去哪，屏幕滚哪
watch(() => activeItemIndex.value, async (newVal) => {
    if (newVal !== -1) {
        await nextTick();
        const activeRow = document.querySelector('.active-pos-row');
        if (activeRow) {
            activeRow.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
    }
});

const handleRowClick = (row) => {
    const idx = cartList.value.findIndex(item => item.id === row.id);
    if (activeItemIndex.value === idx) {
        activeItemIndex.value = -1;
    } else {
        activeItemIndex.value = idx;
    }
}

const getLevelCode = (brandId) => {
    if (!currentMember.value?.id || !brandId) return null;
    return currentMember.value.brandLevels?.[String(brandId)] || null;
}

const handleQtyChange = (index, newVal) => {
    cartList.value[index].qty = newVal;
    runTrial();
}

const tableRowClassName = ({ row, rowIndex }) => {
    const classNames = []
    if (isStockInsufficient(row)) classNames.push('stock-insufficient-row')
    if (rowIndex === activeItemIndex.value) classNames.push('active-pos-row')
    return classNames.join(' ')
}
</script>

<style scoped>
.stock-warning {
    color: #dc2626;
}

/* 库存异常优先于斑马纹；选中行仍保留蓝色焦点。 */
:deep(.el-table__body tr.stock-insufficient-row > td.el-table__cell) {
    background-color: #fef2f2 !important;
}

/* 🌟 击穿原生斑马纹，保证选中行背景泛蓝 */
:deep(.el-table__body tr.active-pos-row > td.el-table__cell) {
    background-color: #e0f2fe !important;
}
</style>
