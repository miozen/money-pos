<template>
    <PageWrapper>
        <div class="grid gap-6 flex-1">
            <MoneyRR :money-crud="moneyCrud">
                <el-input v-model="moneyCrud.query.barcode" placeholder="套餐条码" class="md:!w-48" @keyup.enter.native="moneyCrud.doQuery" />
                <el-input v-model="moneyCrud.query.name" placeholder="套餐名称" class="md:!w-48" @keyup.enter.native="moneyCrud.doQuery" />
                <el-select v-model="moneyCrud.query.status" clearable placeholder="状态" class="md:!w-48" @change="moneyCrud.doQuery">
                    <el-option v-for="item in dict.goodsStatus" :key="item.value" :label="item.desc" :value="item.value" />
                </el-select>
            </MoneyRR>

            <div class="flex items-center gap-2 mb-[-10px]">
                <MoneyCUD :money-crud="moneyCrud" />
            </div>

            <MoneyCrudTable :money-crud="moneyCrud">
                <template #pic="{scope}">
                    <el-image class="w-8 h-8" preview-teleported :src="$money.getOssUrl(scope.row.pic)" :preview-src-list="[$money.getOssUrl(scope.row.pic)]" fit="cover" />
                </template>
                <template #status="{scope}">
                    <el-tag :type="statusColor[scope.row.status] || 'primary'">
                        {{ dict.goodsStatusKv[scope.row.status] }}
                    </el-tag>
                </template>
                <template #opt="{scope}">
                    <MoneyUD :money-crud="moneyCrud" :scope="scope" />
                </template>
            </MoneyCrudTable>

            <MoneyForm :money-crud="moneyCrud" :rules="rules" :dialog-class="'!w-11/12 md:!w-6/12 !mt-12'">
                <el-divider content-position="left">① 套餐基础信息</el-divider>

                <el-form-item label="套餐图片" prop="pic">
                    <el-upload class="avatar-uploader" :auto-upload="false" :show-file-list="false" accept="image/*" :on-change="handlePicSuccess">
                        <img v-if="moneyCrud.form.pic" :src="$money.getOssUrl(moneyCrud.form.pic)" class="w-24" alt="pic">
                        <el-icon v-else class="avatar-uploader-icon !w-24 !h-24"><Plus /></el-icon>
                    </el-upload>
                </el-form-item>

                <div class="md:flex justify-between gap-4">
                    <el-form-item label="套餐条码" prop="barcode" class="!w-full">
                        <el-input v-model.trim="moneyCrud.form.barcode" placeholder="请输入或用扫码枪扫入" />
                    </el-form-item>
                    <el-form-item label="套餐名称" prop="name" class="!w-full">
                        <el-input v-model.trim="moneyCrud.form.name" placeholder="如：中秋特惠大礼包" />
                    </el-form-item>
                </div>

                <div class="md:flex justify-between gap-4">
                    <el-form-item label="套餐售价" prop="salePrice" class="!w-full">
                        <el-input v-model="moneyCrud.form.salePrice" placeholder="直接填一口价" />
                    </el-form-item>
                    <el-form-item label="可售库存" prop="stock" class="!w-full">
                        <el-input-number v-model="moneyCrud.form.stock" :min="0" :step="1" class="!w-full" placeholder="输入预先打包好的数量" />
                    </el-form-item>
                </div>

                <div class="md:flex justify-between gap-4">
                    <el-form-item label="套餐状态" prop="status" class="!w-full">
                        <el-select v-model="moneyCrud.form.status" placeholder="请选择" class="w-full">
                            <el-option v-for="item in dict.goodsStatus" :key="item.value" :label="item.desc" :value="item.value" />
                        </el-select>
                    </el-form-item>
                    <el-form-item label="参与满减" prop="isDiscountParticipable" class="!w-full">
                        <el-switch v-model="moneyCrud.form.isDiscountParticipable" :active-value="1" :inactive-value="0" active-text="允许参与" inactive-text="禁止参与" />
                    </el-form-item>
                </div>

                <el-divider content-position="left">② 套餐包含的单品 (核心配方)</el-divider>

                <div class="mb-2">
                    <el-button type="primary" plain size="small" @click="addSubGoods">
                        <el-icon class="mr-1"><Plus /></el-icon> 增加一件单品
                    </el-button>
                </div>

                <el-table :data="moneyCrud.form.subGoodsList" border size="small" class="w-full mb-4">
                    <el-table-column label="选择商品" min-width="200">
                        <template #default="{row}">
                            <el-select v-model="row.subGoodsId" filterable placeholder="敲汉字或拼音搜索单品" class="w-full">
                                <el-option v-for="item in normalGoodsList" :key="item.id" :label="item.name + ' (￥' + item.salePrice + ')'" :value="item.id">
                                    <span style="float: left">{{ item.name }}</span>
                                    <span style="float: right; color: #8492a6; font-size: 13px">￥{{ item.salePrice }} / 剩 {{ item.stock }}</span>
                                </el-option>
                            </el-select>
                        </template>
                    </el-table-column>
                    <el-table-column label="包含数量" width="120">
                        <template #default="{row}">
                            <el-input-number v-model="row.subGoodsQty" :min="1" size="small" class="!w-full" />
                        </template>
                    </el-table-column>
                    <el-table-column label="操作" width="80" align="center">
                        <template #default="scope">
                            <el-button type="danger" link @click="removeSubGoods(scope.$index)">移除</el-button>
                        </template>
                    </el-table-column>
                </el-table>

                <el-form-item label="描述备注">
                    <el-input v-model.trim="moneyCrud.form.description" type="textarea" maxlength="250" show-word-limit />
                </el-form-item>
            </MoneyForm>
        </div>
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

import { ref, watch, onMounted } from "vue";
import { useUserStore } from "@/store/index.js";
import goodsApi from "@/api/gms/goods.js"
import dictApi from "@/api/system/dict.js";
import { Plus } from '@element-plus/icons-vue'

const userStore = useUserStore()
const normalGoodsList = ref([])

const comboGoodsApi = {
    ...goodsApi,
    list: (params) => {
        if (!params) params = {};
        params.isCombo = 1;
        return goodsApi.list(params);
    }
}

const columns = [
    {prop: 'name', label: '组合套餐名字', width: 180},
    {prop: 'comboDesc', label: '包含的商品 (组合明细)', minWidth: 220},
    {prop: 'salePrice', label: '套餐价格'},
    {prop: 'stock', label: '剩余库存', width: 100, sortable: 'custom'}, // 🌟 把库存列加出来，方便老板监控
    {prop: 'status', label: '状态', width: 100},
    {prop: 'sales', label: '已售出', width: 100, sortable: 'custom'},
    {
        prop: 'opt',
        label: '操作',
        width: 120,
        align: 'center',
        fixed: 'right',
        showOverflowTooltip: false,
        isMoneyUD: true
    },
]

const rules = {
    barcode: [{required: true, message: '请输入套餐条码'}],
    name: [{required: true, message: '请输入套餐名称'}],
    salePrice: [
        {required: true, message: '请输入售价'},
        {pattern: /^[1-9]\d*(\.\d+)?$/, message: '仅支持正数'}
    ],
    stock: [{required: true, message: '请输入初始库存'}], // 🌟 加入必填校验
    status: [{required: true, message: '请选择状态'}]
}

const moneyCrud = ref(new MoneyCrud({
    columns,
    crudMethod: comboGoodsApi,
    optShow: {
        checkbox: userStore.hasPermission(['gmsGoods:edit', 'gmsGoods:del']),
        add: userStore.hasPermission('gmsGoods:add'),
        edit: userStore.hasPermission('gmsGoods:edit'),
        del: userStore.hasPermission('gmsGoods:del')
    },
    query: {
        isCombo: 1
    },
    defaultForm: {
        status: 'SALE',
        isCombo: 1,
        stock: 50, // 🌟 修改默认库存为一个合理值，而不是 999999
        purchasePrice: 0,
        vipPrice: 0,
        coupon: 0,
        isDiscountParticipable: 0 // 🌟 核心：默认禁止套餐参与满减折上折！
    }
}))

watch(() => moneyCrud.value.form.id, (newId) => {
    if (!newId) {
        moneyCrud.value.form.subGoodsList = []
    } else {
        if (!moneyCrud.value.form.subGoodsList) {
            moneyCrud.value.form.subGoodsList = []
        }
    }
}, { immediate: true })

const dict = ref({})
const statusColor = { 'SOLD_OUT': 'warning', 'UN_SHELVE': 'info' }

moneyCrud.value.init(moneyCrud, async () => {
    dict.value = await dictApi.loadDict(["goodsStatus", "goodsStatusKv"])
})

onMounted(async () => {
    const res = await goodsApi.list({ current: 1, size: 1000, isCombo: 0, status: 'SALE' })
    normalGoodsList.value = res.data.records
})

function handlePicSuccess(file) {
    moneyCrud.value.form.pic = URL.createObjectURL(file.raw)
    moneyCrud.value.form.picFile = file.raw
}

function addSubGoods() {
    if (!moneyCrud.value.form.subGoodsList) moneyCrud.value.form.subGoodsList = []
    moneyCrud.value.form.subGoodsList.push({ subGoodsId: null, subGoodsQty: 1 })
}
function removeSubGoods(index) {
    moneyCrud.value.form.subGoodsList.splice(index, 1)
}
</script>