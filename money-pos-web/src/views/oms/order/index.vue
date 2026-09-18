<template>
    <PageWrapper>
        <MoneyRR :money-crud="moneyCrud">
            <el-date-picker v-model="datePicker" type="datetimerange" class="!w-full md:!w-auto"
                            :shortcuts="shortcuts" :default-time="defaultTime" :clearable="false"
                            value-format="YYYY-MM-DD HH:mm:ss"
                            range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期"
                            @change="handleDatePick" />

            <OrderSmartSearch
                class="w-full md:!w-[350px]"
                size="default"
                @select="handleOrderSelect"
                @clear="handleOrderClear"
            />

            <el-select v-model="moneyCrud.query.status" clearable placeholder="状态" class="md:!w-48"
                       @change="moneyCrud.doQuery">
                <el-option v-for="item in dict.orderStatus" :key="item.value" :label="item.desc" :value="item.value" />
            </el-select>
        </MoneyRR>

        <MoneyCUD :money-crud="moneyCrud" class="!justify-center md:!justify-between flex-wrap items-end">
            <div class="flex gap-5 mb-2 md:gap-10">
                <el-statistic title="销售额" :precision="2" :value="orderCount.saleCount" />
                <el-statistic title="成本" :precision="2" :value="orderCount.costCount" />
                <el-statistic title="利润" :precision="2" :value="orderCount.profit" />
            </div>
        </MoneyCUD>

        <MoneyCrudTable :money-crud="moneyCrud">
            <template #address="{scope}">
                {{ scope.row.province + scope.row.city + scope.row.district + scope.row.address }}
            </template>
            <template #profit="{scope}">
                {{ NP.minus(scope.row.payAmount, scope.row.costAmount) }}
            </template>
            <template #status="{scope}">
                <el-tag :type="statusColor[scope.row.status] || 'primary'" effect="dark" class="font-bold">
                    {{ getOrderStatusName(scope.row.status) }}
                </el-tag>
            </template>
            <template #opt="{scope}">
                <el-button plain size="small" @click="showOrderDetail(scope.row.orderNo)" class="mr-2">详情</el-button>

                <el-popconfirm title="确定要退单吗？" @confirm="returnOrder(scope.row)"
                               v-if="scope.row.status !== 'RETURN' && scope.row.status !== 'REFUNDED'">
                    <template #reference>
                        <el-button plain type="danger" size="small">退单</el-button>
                    </template>
                </el-popconfirm>
            </template>
        </MoneyCrudTable>

        <MoneyForm :money-crud="moneyCrud">
            <el-form-item label="表单项" prop="nickname">
                <el-input v-model="moneyCrud.form.name" />
            </el-form-item>
        </MoneyForm>

        <OrderDetailModal v-model="detailVisible" :order-no="currentSearchOrderNo" />

    </PageWrapper>
</template>

<script setup>
import PageWrapper from "@/components/PageWrapper.vue";
import MoneyCrud from '@/components/Crud/MoneyCrud.js'
import MoneyCrudTable from "@/components/Crud/MoneyCrudTable.vue";
import MoneyRR from "@/components/Crud/MoneyRR.vue";
import MoneyCUD from "@/components/Crud/MoneyCUD.vue";
import MoneyForm from "@/components/Crud/MoneyForm.vue";

import OrderSmartSearch from "@/components/common/OrderSmartSearch.vue";
import OrderDetailModal from "@/components/OrderDetailModal.vue";

import {ref} from "vue";
import {useUserStore} from "@/store/index.js";
import { ElMessage } from 'element-plus'
import NP from "number-precision";
import dayjs from "dayjs";
import orderApi from "@/api/oms/order.js";
import dictApi from "@/api/system/dict.js";

const userStore = useUserStore()
const columns = [
    {prop: 'orderNo', label: '订单号', width: 150},
    {prop: 'member', label: '会员'},
    {prop: 'address', label: '地址', show: false},
    {prop: 'costAmount', label: '成本'},
    {prop: 'totalAmount', label: '总价'},
    {prop: 'payAmount', label: '实付款'},
    {prop: 'couponAmount', label: '抵用券'},
    {prop: 'profit', label: '利润'},
    {prop: 'status', label: '状态', width: 100},
    {prop: 'createTime', label: '创建时间', width: 180},
    {prop: 'paymentTime', label: '支付时间', width: 180, show: false},
    {prop: 'completionTime', label: '完成时间', width: 180, show: false},
    {prop: 'remark', label: '备注', show: false},
    {
        prop: 'opt',
        label: '操作',
        width: 150,
        align: 'center',
        fixed: 'right',
        showOverflowTooltip: false,
    },
]
const moneyCrud = ref(new MoneyCrud({
    columns,
    crudMethod: orderApi,
    optShow: {checkbox: false, add: false, edit: false, del: false},
}))

const handleOrderSelect = (order) => {
    moneyCrud.value.query.orderNo = order.orderNo;
    moneyCrud.value.query.member = null;
    moneyCrud.value.doQuery();
}

const handleOrderClear = () => {
    moneyCrud.value.query.orderNo = null;
    moneyCrud.value.query.member = null;
    moneyCrud.value.doQuery();
}

const detailVisible = ref(false)
const currentSearchOrderNo = ref('')

const showOrderDetail = (orderNo) => {
    currentSearchOrderNo.value = orderNo;
    detailVisible.value = true;
}

const dict = ref({ orderStatus: [], orderStatusKv: {} })

// 🌟 核心：统一的状态解析逻辑（强制翻译字典，字典未加载时硬编码兜底）
const getOrderStatusName = (status) => {
    if (!status) return '-';
    // 1. 优先尝试从后端加载的 Key-Value 映射里找
    if (dict.value?.orderStatusKv && dict.value.orderStatusKv[status]) {
        return dict.value.orderStatusKv[status];
    }
    // 2. 备选：从后端原始数组里找
    const statuses = dict.value?.orderStatus;
    if (Array.isArray(statuses)) {
        const match = statuses.find(s => s.value === status || s.dictValue === status);
        if (match) return match.desc || match.dictLabel || status;
    }
    // 3. 终极兜底补丁：专门解决截图中的 "REFUNDED" 原始英文显示问题
    const fallbackMap = {
        'PAID': '已支付',
        'DONE': '已完成',
        'PARTIAL': '部分退货',
        'PARTIAL_REFUNDED': '部分退货',
        'REFUNDED': '已退款',
        'RETURN': '已退款',
        'CLOSED': '已关闭',
        'CANCELLED': '已取消',
        'UNPAID': '待支付'
    };
    return fallbackMap[status] || status;
}

const statusColor = {
    'RETURN': 'info',
    'REFUNDED': 'info',
    'PAID': 'success',
    'DONE': 'success',
    'PARTIAL': 'warning',
    'PARTIAL_REFUNDED': 'warning',
    'CLOSED': 'info'
}

const datePicker = ref([dayjs().startOf('M').format('YYYY-MM-DD HH:mm:ss'), dayjs().endOf('M').format('YYYY-MM-DD HH:mm:ss')])
const defaultTime = [dayjs().startOf('d').toDate(), dayjs().endOf('d').toDate()]
const shortcuts = [
    { text: '今天', value() { return [dayjs().startOf('d').toDate(), dayjs().endOf('d').toDate()] } },
    { text: '本月', value() { return [dayjs().startOf('M').toDate(), dayjs().endOf('M').toDate()] } },
    { text: '最近7天', value() { return [dayjs().subtract(6, 'd').startOf('d').toDate(), dayjs().endOf('d').toDate()] } },
    { text: '最近30天', value() { return [dayjs().subtract(29, 'd').startOf('d').toDate(), dayjs().endOf('d').toDate()] } }
]

moneyCrud.value.init(moneyCrud, async () => {
    dict.value = await dictApi.loadDict(["orderStatus"])
    moneyCrud.value.query.startTime = datePicker.value[0]
    moneyCrud.value.query.endTime = datePicker.value[1]
})

const orderCount = ref({ saleCount: 0, costCount: 0, profit: 0 })
moneyCrud.value.Hook.afterDoQuery = () => {
    orderApi.getCount(moneyCrud.value.query)
        .then(res => {
            const {data} = res
            orderCount.value.saleCount = data.saleCount
            orderCount.value.costCount = data.costCount
            orderCount.value.profit = data.profit
        })
}

function handleDatePick(value) {
    moneyCrud.value.query.startTime = value[0]
    moneyCrud.value.query.endTime = value[1]
    moneyCrud.value.doQuery()
}

function returnOrder(row) {
    orderApi.returnOrder({
        orderNo: row.orderNo,
        reqId: 'B_RET' + Date.now()
    }).then(() => {
        moneyCrud.value.messageOk()
        moneyCrud.value.doQuery()
    }).catch((e) => {
        ElMessage.error(e.msg || e.message || '退款失败');
    })
}
</script>