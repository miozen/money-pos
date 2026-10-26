<template>
    <PageWrapper>
        <div class="bg-white p-4 rounded-lg shadow-sm border border-gray-100 mb-4 flex justify-between items-center">
            <div class="flex items-center gap-4">
                <el-input v-model="queryParams.orderNo" placeholder="订单号" class="!w-64" clearable @keyup.enter.native="fetchData" />
                <el-checkbox v-model="isAnomalyOnly" @change="handleAnomalyChange" border size="default" class="!text-red-500 font-bold">
                    <el-icon class="mr-1"><WarningFilled /></el-icon> 仅看异常数据 (缺失成本/负毛利)
                </el-checkbox>
            </div>
            <el-button type="primary" @click="fetchData" :loading="loading" class="font-bold tracking-widest px-8">执行审计</el-button>
        </div>

        <div class="bg-white rounded-lg shadow-sm border border-gray-100 p-2 h-[calc(100vh-220px)] flex flex-col">
            <el-table
                :data="tableData"
                v-loading="loading"
                stripe
                border
                height="100%"
                class="w-full text-sm font-mono"
            >
                <el-table-column prop="createTime" label="交易时间" width="165" align="center" fixed="left" />

                <el-table-column prop="orderNo" label="关联单号" width="180" align="center">
                    <template #default="{row}">
                        <span
                            class="text-blue-600 font-bold tracking-wider cursor-pointer hover:underline hover:text-blue-800 transition-colors"
                            @click="openOrderPenetration(row.orderNo)"
                        >
                            {{ row.orderNo }}
                        </span>
                    </template>
                </el-table-column>

                <el-table-column prop="goodsName" label="商品" min-width="180" show-overflow-tooltip>
                    <template #default="{row}">
                        <span class="text-gray-800 font-bold">{{ row.goodsName }}</span>
                    </template>
                </el-table-column>

                <el-table-column label="交易价格" align="center">
                    <el-table-column prop="salePrice" label="吊牌价" width="90" align="right">
                        <template #default="{row}"><span class="text-gray-400 line-through">￥{{ row.salePrice }}</span></template>
                    </el-table-column>

                    <el-table-column prop="goodsPrice" label="实际客单" width="100" align="right">
                        <template #default="{row}"><span class="font-bold text-gray-800">￥{{ row.goodsPrice }}</span></template>
                    </el-table-column>

                    <el-table-column prop="purchasePrice" label="进价快照" width="100" align="right">
                        <template #default="{row}">
                            <div :class="row.isMissingCost === 1 ? 'bg-red-100 text-red-600 px-1 rounded animate-pulse' : 'text-blue-600'">
                                ￥{{ row.purchasePrice }}
                                <el-icon v-if="row.isMissingCost === 1" class="ml-1"><Warning /></el-icon>
                            </div>
                        </template>
                    </el-table-column>
                </el-table-column>

                <el-table-column label="利润情况" align="center">
                    <el-table-column prop="unitProfit" label="单件净利" width="110" align="right">
                        <template #default="{row}">
                            <span :class="row.unitProfit < 0 ? 'text-red-600 font-black' : 'text-green-600 font-bold'">
                                {{ row.unitProfit > 0 ? '+' : '' }}￥{{ row.unitProfit }}
                            </span>
                        </template>
                    </el-table-column>

                    <el-table-column prop="profitMargin" label="毛利率" width="120" align="right" fixed="right">
                        <template #default="{row}">
                            <div v-if="row.isMissingCost === 1" class="text-gray-400 text-xs">缺失成本</div>
                            <div v-else :class="row.profitMargin < 0 ? 'bg-red-50 text-red-600 border border-red-200' : 'bg-green-50 text-green-700 border border-green-200'" class="px-2 py-1 rounded font-black tracking-widest">
                                {{ (row.profitMargin * 100).toFixed(2) }}%
                            </div>
                        </template>
                    </el-table-column>
                </el-table-column>
            </el-table>

            <div class="flex justify-end mt-4 pr-4">
                <el-pagination
                    v-model:current-page="queryParams.current"
                    v-model:page-size="queryParams.size"
                    :page-sizes="[20, 50, 100]"
                    :total="total"
                    layout="total, sizes, prev, pager, next, jumper"
                    @size-change="fetchData"
                    @current-change="fetchData"
                />
            </div>
        </div>

        <OrderDetailModal v-model="modalVisible" :order-no="currentOrderNo" />

    </PageWrapper>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { WarningFilled, Warning } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageWrapper from "@/components/PageWrapper.vue"
import orderApi from '@/api/oms/order.js'

// 🌟 引入刚封装好的标准件
import OrderDetailModal from "@/components/OrderDetailModal.vue"

const loading = ref(false)
const tableData = ref([])
const total = ref(0)

const queryParams = ref({
    current: 1,
    size: 20,
    orderNo: '',
    status: ''
})

const isAnomalyOnly = ref(false)

const modalVisible = ref(false)
const currentOrderNo = ref('')

onMounted(() => {
    fetchData()
})

const handleAnomalyChange = (val) => {
    queryParams.value.status = val ? 'ANOMALY' : ''
    queryParams.value.current = 1
    fetchData()
}

const fetchData = async () => {
    loading.value = true
    try {
        const res = await orderApi.getProfitAuditPage(queryParams.value)
        const data = res?.data || res || {}
        tableData.value = data?.records || data || []
        total.value = data?.total || 0
    } catch (error) {
        ElMessage.error('审计数据获取失败')
    } finally {
        loading.value = false
    }
}

// 穿透操作，点火即开
const openOrderPenetration = (orderNo) => {
    currentOrderNo.value = orderNo
    modalVisible.value = true
}
</script>
