<template>
    <PageWrapper>
        <MoneyRR :money-crud="moneyCrud">
            <el-input v-model="moneyCrud.query.name" placeholder="券名称" class="md:!w-48" @keyup.enter.native="moneyCrud.doQuery" />
        </MoneyRR>
        <MoneyCUD :money-crud="moneyCrud" />
        <MoneyCrudTable :money-crud="moneyCrud">
            <template #status="{scope}">
                <el-switch v-model="scope.row.status" :active-value="1" :inactive-value="0" @change="changeStatus(scope.row)" />
            </template>
            <template #opt="{scope}">
                <MoneyUD :money-crud="moneyCrud" :scope="scope" />
            </template>
        </MoneyCrudTable>
        <MoneyForm :money-crud="moneyCrud" :rules="rules" :dialog-class="'!w-11/12 md:!w-4/12 !mt-24'">
            <el-form-item label="券名称" prop="name">
                <el-input v-model.trim="moneyCrud.form.name" placeholder="例如：周末特惠满100减10" />
            </el-form-item>
            <div class="flex justify-between gap-2">
                <el-form-item label="使用门槛" prop="thresholdAmount" class="!w-full">
                    <el-input-number v-model="moneyCrud.form.thresholdAmount" :min="0" :precision="2" :step="10" class="!w-full" placeholder="满多少元可用" />
                </el-form-item>
                <el-form-item label="减免金额" prop="discountAmount" class="!w-full">
                    <el-input-number v-model="moneyCrud.form.discountAmount" :min="0.01" :precision="2" :step="5" class="!w-full" placeholder="减多少元" />
                </el-form-item>
            </div>
            <el-form-item label="状态" prop="status">
                <el-switch v-model="moneyCrud.form.status" :active-value="1" :inactive-value="0" active-text="生效 (可发放)" inactive-text="停用" />
            </el-form-item>
        </MoneyForm>
    </PageWrapper>
</template>

<script setup>
import PageWrapper from "@/components/PageWrapper.vue";
import MoneyCrud from '@/components/Crud/MoneyCrud.js'
import MoneyCrudTable from "@/components/Crud/MoneyCrudTable.vue";
import MoneyRR from "@/components/Crud/MoneyRR.vue";
import MoneyCUD from "@/components/Crud/MoneyCUD.vue";
import MoneyUD from "@/components/Crud/MoneyUD.vue";
import MoneyForm from "@/components/Crud/MoneyForm.vue";
import { ref } from "vue";
import couponRuleApi from "@/api/pos/couponRule.js";
import { ElMessage } from "element-plus";

const columns = [
    {prop: 'name', label: '券名称'},
    {prop: 'thresholdAmount', label: '使用门槛 (元)'},
    {prop: 'discountAmount', label: '减免金额 (元)'},
    {prop: 'status', label: '状态'},
    {prop: 'createTime', label: '创建时间', width: 180},
    {
        prop: 'opt',
        label: '操作',
        width: 120,
        align: 'center',
        fixed: 'right',
        showOverflowTooltip: false,
        isMoneyUD: true
    }
]

const rules = {
    name: [{required: true, message: '请输入券名称'}],
    thresholdAmount: [{required: true, type: 'number', message: '请输入使用门槛'}],
    discountAmount: [{required: true, type: 'number', message: '请输入减免金额'}]
}

const moneyCrud = ref(new MoneyCrud({
    columns,
    crudMethod: couponRuleApi,
    optShow: { checkbox: true, add: true, edit: true, del: true },
    defaultForm: { status: 1, thresholdAmount: 100, discountAmount: 10 }
}))

moneyCrud.value.init(moneyCrud)

function changeStatus(row) {
    couponRuleApi.edit({ id: row.id, status: row.status }).then(() => {
        ElMessage.success('状态更新成功')
    }).catch(() => {
        row.status = row.status === 1 ? 0 : 1
    })
}
</script>