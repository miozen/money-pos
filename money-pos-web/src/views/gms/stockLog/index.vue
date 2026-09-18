<template>
    <PageWrapper>
        <MoneyRR :money-crud="moneyCrud">
            <SmartGoodsSelector
                v-model="moneyCrud.query.goodsBarcode"
                mode="report"
                placeholder="搜商品名称 / 条码 / 拼音"
                class="md:!w-64"
                @change="moneyCrud.doQuery"
                @search="moneyCrud.doQuery"
            />

            <el-input v-model="moneyCrud.query.orderNo" placeholder="搜关联单号" class="md:!w-48" @keyup.enter.native="moneyCrud.doQuery" />
            <el-select v-model="moneyCrud.query.type" placeholder="过滤变动类型" clearable class="md:!w-48" @change="moneyCrud.doQuery">
                <el-option label="🛒 销售出库 (SALE)" value="SALE" />
                <el-option label="🔄 售后退回 (RETURN)" value="RETURN" />
                <el-option label="📦 采购入库 (INBOUND)" value="INBOUND" />
                <el-option label="⚖️ 盘点校准 (CHECK)" value="CHECK" />
                <el-option label="🗑️ 报损出库 (SCRAP)" value="SCRAP" />
            </el-select>
        </MoneyRR>

        <MoneyCrudTable :money-crud="moneyCrud">
            <template #type="{scope}">
                <el-tag v-if="scope.row.type === 'SALE'" type="danger" effect="light" class="w-[100px] text-center font-bold">🛒 销售</el-tag>
                <el-tag v-else-if="scope.row.type === 'RETURN'" type="warning" effect="light" class="w-[100px] text-center font-bold">🔄 退货</el-tag>
                <el-tag v-else-if="scope.row.type === 'INBOUND'" type="success" effect="light" class="w-[100px] text-center font-bold">📦 入库</el-tag>
                <el-tag v-else-if="scope.row.type === 'CHECK'" type="primary" effect="light" class="w-[100px] text-center font-bold">⚖️ 盘点</el-tag>
                <el-tag v-else-if="scope.row.type === 'SCRAP'" type="info" effect="dark" class="w-[100px] text-center font-bold">🗑️ 报损</el-tag>
                <span v-else>{{ scope.row.type }}</span>
            </template>

            <template #quantity="{scope}">
                <span :class="scope.row.quantity > 0 ? 'text-green-600' : 'text-red-600'" class="font-black text-lg tracking-wider">
                    {{ scope.row.quantity > 0 ? '+' : '' }}{{ scope.row.quantity }}
                </span>
            </template>

            <template #afterQuantity="{scope}">
                <span class="text-gray-800 font-mono font-bold">{{ scope.row.afterQuantity }}</span>
            </template>

            <template #orderNo="{scope}">
                <el-link v-if="scope.row.orderNo" type="primary" :underline="false" @click="showDetail(scope.row)">
                    <span class="font-mono font-bold tracking-wider flex items-center gap-1 hover:text-blue-800">
                        <el-icon><Document /></el-icon>{{ scope.row.orderNo }}
                    </span>
                </el-link>
                <span v-else class="text-gray-400">-</span>
            </template>
        </MoneyCrudTable>

        <OrderDetailModal v-model="orderDetailVisible" :order-no="currentSearchOrderNo" />

        <el-dialog v-model="otherDetailVisible" :title="detailTitle" width="900px" destroy-on-close>
            <div class="mb-4 flex justify-between items-center bg-gray-50 p-3 rounded border border-dashed">
                <div class="text-sm text-gray-500">
                    单据编号: <span class="font-mono font-bold text-gray-800">{{ currentSearchOrderNo }}</span>
                </div>
                <div class="text-sm">
                    本次合计盈亏:
                    <span :class="totalImpact >= 0 ? 'text-green-600' : 'text-red-600'" class="text-xl font-black font-mono ml-2">
                        {{ totalImpact >= 0 ? '+' : '' }}￥{{ totalImpact.toFixed(2) }}
                    </span>
                </div>
            </div>

            <el-table :data="detailList" v-loading="detailLoading" stripe border size="default" class="w-full">
                <el-table-column type="index" label="#" width="50" align="center" />
                <el-table-column prop="goodsName" label="商品信息" min-width="180" show-overflow-tooltip>
                    <template #default="{row}">
                        <div class="font-bold text-gray-700">{{ row.goodsName }}</div>
                        <div class="text-[10px] text-gray-400 font-mono">{{ row.goodsBarcode }}</div>
                    </template>
                </el-table-column>

                <el-table-column label="成本快照" width="130" align="right">
                    <template #default="{row}">
                        <span class="text-gray-500 font-mono">￥{{ Number(row.costPriceSnapshot || 0).toFixed(2) }}</span>
                    </template>
                </el-table-column>

                <el-table-column prop="quantity" label="数量变动" width="120" align="center">
                    <template #default="{row}">
                        <span class="font-black text-lg" :class="row.quantity > 0 ? 'text-green-600' : 'text-red-600'">
                            {{ row.quantity > 0 ? '+' : '' }}{{ row.quantity }}
                        </span>
                    </template>
                </el-table-column>

                <el-table-column label="资产影响 (盈亏)" width="150" align="right">
                    <template #default="{row}">
                        <span class="font-bold font-mono text-base" :class="row.impactAmount >= 0 ? 'text-green-600' : 'text-red-600'">
                            {{ row.impactAmount >= 0 ? '+' : '' }}￥{{ Number(row.impactAmount || 0).toFixed(2) }}
                        </span>
                    </template>
                </el-table-column>

                <el-table-column prop="remark" label="备注" show-overflow-tooltip />
            </el-table>

            <template #footer>
                <div class="text-xs text-gray-400 text-left italic">
                    * 成本快照记录了变动发生时的移动加权平均单价（入库除外，记录为录入价）。
                </div>
            </template>
        </el-dialog>
    </PageWrapper>
</template>

<script setup>
import PageWrapper from "@/components/PageWrapper.vue";
import MoneyCrud from '@/components/Crud/MoneyCrud.js'
import MoneyCrudTable from "@/components/Crud/MoneyCrudTable.vue";
import MoneyRR from "@/components/Crud/MoneyRR.vue";
import stockLogApi from "@/api/gms/stockLog.js";
import { req } from "@/api/index.js";
import { ref, computed } from "vue";
import { Document } from "@element-plus/icons-vue";

import OrderDetailModal from "@/components/OrderDetailModal.vue";
import SmartGoodsSelector from "@/components/common/SmartGoodsSelector.vue"

const columns = [
    {prop: 'createTime', label: '发生时间', width: 170},
    {prop: 'goodsName', label: '商品名称', minWidth: 160, showOverflowTooltip: true},
    {prop: 'goodsBarcode', label: '商品条码', width: 140},
    {prop: 'type', label: '变动类型', width: 130, align: 'center'},
    {prop: 'quantity', label: '变动数量', width: 110, align: 'center'},
    {prop: 'afterQuantity', label: '结余库存', width: 110, align: 'center'},
    {prop: 'orderNo', label: '关联单号', width: 190, align: 'center'},
    {prop: 'remark', label: '备注说明', minWidth: 150},
]

const moneyCrud = ref(new MoneyCrud({
    columns,
    crudMethod: stockLogApi,
    optShow: { add: false, edit: false, del: false }
}))

moneyCrud.value.init(moneyCrud)

const orderDetailVisible = ref(false)
const currentSearchOrderNo = ref('')

const otherDetailVisible = ref(false)
const detailLoading = ref(false)
const detailList = ref([])
const detailTitle = ref('')

const totalImpact = computed(() => {
    return detailList.value.reduce((sum, item) => sum + (Number(item.impactAmount) || 0), 0);
});

const showDetail = async (row) => {
    currentSearchOrderNo.value = row.orderNo;

    if (row.type === 'SALE' || row.type === 'RETURN') {
        orderDetailVisible.value = true;
    } else {
        const typeName = { 'INBOUND': '采购入库单', 'CHECK': '盘点单', 'SCRAP': '报损单' }[row.type] || '单据'
        detailTitle.value = `📊 ${typeName}审计 - ${row.orderNo}`
        otherDetailVisible.value = true;
        detailLoading.value = true;
        detailList.value = [];
        try {
            const res = await req({ url: '/gms/stockLog', method: 'GET', params: { orderNo: row.orderNo, size: 500 } })
            detailList.value = res.data?.records || res || []
        } catch (e) {
            console.error(e)
        } finally {
            detailLoading.value = false
        }
    }
}
</script>