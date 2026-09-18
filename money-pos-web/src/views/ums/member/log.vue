<template>
    <PageWrapper>
        <MoneyRR :money-crud="moneyCrud">
            <MemberSmartSearch
                class="w-[350px] md:!w-[400px]"
                size="default"
                placeholder="搜索会员(支持名/号/手机)"
                @select="handleMemberSearch"
                @clear="handleMemberClear"
            />

            <el-select v-model="moneyCrud.query.type" placeholder="资金类型" clearable class="md:!w-48">
                <el-option label="🔵 会员余额 (BALANCE)" value="BALANCE" />
                <el-option label="🟢 会员券 (COUPON)" value="COUPON" />
                <el-option label="🟠 满减券 (VOUCHER)" value="VOUCHER" />
            </el-select>
            <el-select v-model="moneyCrud.query.operateType" placeholder="业务类型" clearable class="md:!w-40">
                <el-option label="在线充值" value="RECHARGE" />
                <el-option label="消费支出" value="CONSUME" />
                <el-option label="售后退回" value="REFUND" />
                <el-option label="系统导入" value="IMPORT" />
                <el-option label="充值赠送" value="GIFT" />
                <el-option label="活动发放" value="ISSUE" />
                <el-option label="红冲撤销" value="REVERSAL" />
            </el-select>
        </MoneyRR>

        <MoneyCrudTable :money-crud="moneyCrud">
            <template #createTime="{scope}">
                <div v-if="scope.row.createTime" class="flex flex-col leading-tight text-xs justify-center h-full">
                    <span class="text-gray-500">{{ scope.row.createTime.split(' ')[0] }}</span>
                    <span class="font-mono font-bold text-gray-800">{{ scope.row.createTime.split(' ')[1] }}</span>
                </div>
                <span v-else class="text-gray-300">-</span>
            </template>

            <template #memberName="{scope}">
                <el-link type="primary" :underline="false" @click="openMember360(scope.row)">
                    <span class="font-bold text-blue-600 hover:text-blue-800 tracking-wider">
                        {{ scope.row.memberName || '未知' }}
                    </span>
                </el-link>
            </template>

            <template #operateType="{scope}">
                <el-tag :type="getTagType(scope.row.operateType)" effect="plain">{{ getTypeName(scope.row.operateType) }}</el-tag>
            </template>

            <template #type="{scope}">
                <el-tag v-if="scope.row.type === 'BALANCE'" type="primary" effect="light" class="w-[110px] text-center tracking-widest font-bold">🔵 会员余额</el-tag>
                <el-tag v-else-if="scope.row.type === 'COUPON'" type="success" effect="light" class="w-[110px] text-center tracking-widest font-bold">🟢 会员券</el-tag>
                <el-tag v-else-if="scope.row.type === 'VOUCHER'" type="warning" effect="light" class="w-[110px] text-center tracking-widest font-bold">🟠 满减券</el-tag>
                <span v-else>{{ scope.row.type }}</span>
            </template>

            <template #amount="{scope}">
                <span v-if="scope.row.type === 'VOUCHER'" :class="scope.row.amount > 0 ? 'text-green-600' : 'text-red-600'" class="font-bold text-sm">
                    {{ scope.row.amount > 0 ? '+' : '' }}{{ Math.floor(scope.row.amount) }} 张
                </span>
                <MoneyDisplay v-else :value="scope.row.amount" show-sign auto-color size="sm" />
            </template>

            <template #realAmount="{scope}">
                <span v-if="scope.row.realAmount != null && scope.row.realAmount > 0" class="bg-orange-50 px-1 py-0.5 rounded border border-orange-100">
                    <MoneyDisplay :value="scope.row.realAmount" size="sm" color="text-orange-600" />
                </span>
                <span v-else-if="scope.row.operateType === 'RECHARGE' && scope.row.realAmount === 0" class="text-gray-400 font-medium text-sm">
                    ￥0.00
                </span>
                <span v-else class="text-gray-300">-</span>
            </template>

            <template #afterAmount="{scope}">
                <span v-if="scope.row.type === 'VOUCHER'" class="text-gray-700 font-bold text-sm">
                    {{ Math.floor(scope.row.afterAmount) }} 张
                </span>
                <MoneyDisplay v-else :value="scope.row.afterAmount" size="sm" color="text-gray-700" />
            </template>

            <template #orderNo="{scope}">
                <el-link v-if="scope.row.orderNo" type="primary" :underline="false" @click="showOrderDetail(scope.row.orderNo)">
                    <span class="font-mono font-bold tracking-wider text-blue-600 hover:text-blue-800 flex items-center gap-1">
                        <el-icon><Document /></el-icon>{{ scope.row.orderNo }}
                    </span>
                </el-link>
                <span v-else class="text-gray-300">-</span>
            </template>
        </MoneyCrudTable>

        <OrderDetailModal v-model="salesDetailVisible" :order-no="currentSearchOrderNo" />

        <RechargeOrderDetail
            v-model="rechargeDetailVisible"
            :order-no="currentSearchOrderNo"
            @refresh="moneyCrud.doQuery()"
        />

        <MemberProfileModal
            v-model="profileVisible"
            :member-id="current360Member.id"
            :member-info="current360Member"
            :brands-dict="brandsKv"
            :levels-dict="dict.memberTypeKv"
        />
    </PageWrapper>
</template>

<script setup>
import PageWrapper from "@/components/PageWrapper.vue";
import MoneyCrud from '@/components/Crud/MoneyCrud.js'
import MoneyCrudTable from "@/components/Crud/MoneyCrudTable.vue";
import MoneyRR from "@/components/Crud/MoneyRR.vue";
import memberLogApi from "@/api/ums/memberLog.js";
import { ref, onBeforeMount } from "vue";
import { Document } from '@element-plus/icons-vue'

// 🌟 核心：把我误删的这行 req 请求工具加回来了！
import { req } from "@/api/index.js";
import { ElMessage } from 'element-plus'

import dictApi from "@/api/system/dict.js"
import brandApi from "@/api/gms/brand.js"

import OrderDetailModal from "@/components/OrderDetailModal.vue";
import RechargeOrderDetail from "@/views/ums/member/components/RechargeOrderDetail.vue";
import MemberSmartSearch from "@/components/common/MemberSmartSearch.vue";
import MemberProfileModal from "@/components/common/MemberProfileModal.vue";

const columns = [
    {prop: 'createTime', label: '发生时间', width: 95, align: 'center'},
    {prop: 'memberName', label: '会员姓名', minWidth: 90, showOverflowTooltip: true},
    {prop: 'memberPhone', label: '手机号', minWidth: 130},
    {prop: 'operateType', label: '业务类型', width: 100, align: 'center'},
    {prop: 'type', label: '资金账户', width: 140, align: 'center'},
    {prop: 'amount', label: '变动明细', minWidth: 130, align: 'right'},
    {prop: 'realAmount', label: '实收现金', minWidth: 120, align: 'right'},
    {prop: 'afterAmount', label: '变动后余额', minWidth: 130, align: 'right'},
    {prop: 'orderNo', label: '关联单号', minWidth: 170, align: 'center'},
    {prop: 'remark', label: '备注说明', minWidth: 200},
]

const moneyCrud = ref(new MoneyCrud({
    columns,
    crudMethod: memberLogApi,
    optShow: { add: false, edit: false, del: false }
}))

moneyCrud.value.query.size = 20;
moneyCrud.value.init(moneyCrud)

const dict = ref({})
const brandsKv = ref({})

const fetchBaseData = async () => {
    try {
        const dictRes = await dictApi.loadDict(["memberType"])
        dict.value = dictRes || {}
        const brandRes = await (brandApi.list ? brandApi.list({ size: 1000 }) : brandApi.getSelect())
        const brandList = brandRes?.data?.records || brandRes?.data || brandRes?.records || brandRes || []
        brandList.forEach(e => { brandsKv.value[e.id || e.value] = e.name || e.label })
    } catch (e) {
        console.error("字典加载失败", e)
    }
}
onBeforeMount(() => { fetchBaseData() })

const handleMemberSearch = (member) => {
    moneyCrud.value.query.phone = member.phone;
    moneyCrud.value.doQuery();
}
const handleMemberClear = () => {
    moneyCrud.value.query.phone = null;
    moneyCrud.value.doQuery();
}

const getTypeName = (type) => {
    const map = { 'RECHARGE': '充值', 'CONSUME': '消费', 'IMPORT': '系统导入', 'GIFT': '赠送', 'REFUND': '退货退款', 'ISSUE': '发券', 'REVERSAL': '红冲撤销' }
    return map[type] || type
}
const getTagType = (type) => {
    const map = { 'RECHARGE': 'success', 'CONSUME': 'danger', 'IMPORT': 'warning', 'GIFT': 'primary', 'ISSUE': 'warning', 'REFUND': 'info', 'REVERSAL': 'info' }
    return map[type] || ''
}

const salesDetailVisible = ref(false)
const rechargeDetailVisible = ref(false)
const currentSearchOrderNo = ref('')

const showOrderDetail = (orderNo) => {
    if (!orderNo) return;
    currentSearchOrderNo.value = orderNo;
    if (orderNo.startsWith('RC')) {
        rechargeDetailVisible.value = true;
    } else {
        salesDetailVisible.value = true;
    }
}

const profileVisible = ref(false)
const current360Member = ref({})

// 🌟 这里保留着您原本寻找真 ID 的逻辑，并配合弹窗特种兵使用
const openMember360 = async (row) => {
    if (!row.memberPhone) {
        ElMessage.warning('该流水缺失手机号，无法溯源会员档案');
        return;
    }

    try {
        const res = await req({
            url: '/ums/member',
            method: 'GET',
            params: { phone: row.memberPhone, current: 1, size: 1 }
        });

        let memberList = [];
        if (res && res.data && res.data.records) {
            memberList = res.data.records;
        } else if (res && res.records) {
            memberList = res.records;
        } else if (Array.isArray(res)) {
            memberList = res;
        }

        if (memberList.length > 0) {
            const realMember = memberList[0];

            current360Member.value = {
                id: realMember.id, // 拿到真会员 ID！
                name: realMember.name,
                phone: realMember.phone
            };
            profileVisible.value = true; // 唤醒弹窗特种兵
        } else {
            ElMessage.warning(`数据库中已无此会员档案`);
        }
    } catch (e) {
        console.error("查无此人：", e);
        ElMessage.error('调取会员档案失败，请检查网络');
    }
}
</script>