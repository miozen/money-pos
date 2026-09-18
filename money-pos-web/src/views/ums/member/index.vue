<template>
    <PageWrapper>
        <MemberToolbar :money-crud="moneyCrud" />

        <MoneyCrudTable :money-crud="moneyCrud" class="mt-4">
            <template #name="{scope}">
                <el-link type="primary" :underline="false" @click="openMember360(scope.row)">
                    <span class="font-bold text-blue-600 flex items-center gap-1 tracking-widest">
                        <el-icon><DataLine /></el-icon> {{ scope.row.name }}
                    </span>
                </el-link>
            </template>
            <template #brandLevels="{scope}">
                <div class="flex flex-wrap gap-1">
                    <template v-if="scope.row.brandLevels && Object.keys(scope.row.brandLevels).length > 0">
                        <el-tag v-for="(levelCode, brandId) in scope.row.brandLevels" :key="brandId" size="small" type="success" effect="light" class="font-bold">
                            {{ brandsKv[brandId] || '未知' }}: {{ dict.memberTypeKv[levelCode] || levelCode }}
                        </el-tag>
                    </template>
                    <el-tag v-else size="small" type="info" class="text-gray-400">普通零售客</el-tag>
                </div>
            </template>
            <template #opt="{scope}">
                <el-button type="success" link @click="openRecharge(scope.row)">
                    <el-icon class="mr-1"><MoneyIcon /></el-icon>业务办理
                </el-button>
                <MoneyUD :money-crud="moneyCrud" :scope="scope" />
            </template>
        </MoneyCrudTable>

        <MoneyForm :money-crud="moneyCrud" :rules="rules" dialog-class="!w-11/12 md:!w-5/12 !mt-12">
            <div :key="formKey" class="w-full min-h-[300px]">
                <MemberFormContent
                    :form="moneyCrud.form"
                    :brands="brands"
                    :member-types="dict.memberType"
                />
            </div>
        </MoneyForm>

        <MemberProfileModal
            v-model="profileVisible"
            :member-id="currentMember.id"
            :member-info="currentMember"
            :brands-dict="brandsKv"
            :levels-dict="dict.memberTypeKv"
        />

        <MemberRechargeDialog v-model="rechargeVisible" :member-info="currentMember" @success="moneyCrud.doQuery()" />
    </PageWrapper>
</template>

<script setup>
import { ref, watch } from "vue";
import MoneyCrud from '@/components/Crud/MoneyCrud.js';
import MoneyCrudTable from "@/components/Crud/MoneyCrudTable.vue";
import MoneyForm from "@/components/Crud/MoneyForm.vue";
import MoneyUD from "@/components/Crud/MoneyUD.vue";
import PageWrapper from "@/components/PageWrapper.vue";

import MemberToolbar from "./components/MemberToolbar.vue";
import MemberFormContent from "./components/MemberFormContent.vue";
import MemberRechargeDialog from "./components/MemberRechargeDialog.vue";
import MemberProfileModal from "@/components/common/MemberProfileModal.vue";

import memberApi from "@/api/ums/member.js";
import dictApi from "@/api/system/dict.js";
import brandApi from "@/api/gms/brand.js";
import { DataLine, Money as MoneyIcon } from "@element-plus/icons-vue";

const dict = ref({});
const brands = ref([]);
const brandsKv = ref({});
const formKey = ref(Date.now());

const profileVisible = ref(false);
const rechargeVisible = ref(false);
const currentMember = ref({});

const openMember360 = (row) => { currentMember.value = row; profileVisible.value = true; };
const openRecharge = (row) => { currentMember.value = row; rechargeVisible.value = true; };

// 核心审计拦截：将组件内部的 _brandLevels 同步给后端
const hookedApi = {
    ...memberApi,
    add: (data) => {
        const payload = { ...data };
        payload.brandLevels = payload._brandLevels || {};
        payload.type = 'MEMBER';
        delete payload._brandLevels;
        return memberApi.add(payload);
    },
    edit: (data) => {
        const payload = { ...data };
        payload.brandLevels = payload._brandLevels || {};
        delete payload._brandLevels;
        return memberApi.edit(payload);
    }
};

const moneyCrud = ref(new MoneyCrud({
    columns: [
        {prop: 'name', label: '会员名称', width: 120},
        {prop: 'phone', label: '手机号码', width: 130},
        {prop: 'brandLevels', label: '品牌身份', minWidth: 260},
        {prop: 'balance', label: '余额', width: 100},
        {prop: 'coupon', label: '会员券', width: 100},
        {prop: 'voucherCount', label: '满减券', width: 80},
        {prop: 'opt', label: '操作', width: 220, align: 'center', fixed: 'right', isMoneyUD: true},
    ],
    crudMethod: hookedApi,
    defaultForm: { type: 'MEMBER', coupon: 0 }
}));

const rules = {
    name: [{required: true, message: '名必填'}],
    phone: [
        {required: true, message: '手机必填'},
        {pattern: /^1[3-9]\d{9}$/, message: '格式错误'}
    ]
};

watch(() => moneyCrud.value.box, (open) => { if (open) formKey.value = Date.now(); });

moneyCrud.value.init(moneyCrud, async () => {
    dict.value = await dictApi.loadDict(["memberType"]);
    if (dict.value.memberType) dict.value.memberType = dict.value.memberType.filter(i => i.value !== 'MEMBER');
    const bRes = await brandApi.getSelect();
    brands.value = bRes.data || [];
    brands.value.forEach(e => { brandsKv.value[e.value] = e.label; });
});
</script>