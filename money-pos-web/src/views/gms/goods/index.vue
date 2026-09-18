<template>
    <PageWrapper>
        <div class="flex">
            <div class="mr-6 w-2/12 hidden lg:block">
                <GoodsCategory @node-click="selectGoodsCategory" />
            </div>

            <div class="grid gap-6 flex-1">
                <GoodsToolbar :money-crud="moneyCrud" :brands="brands" :categories="categories" :dict="dict" />

                <GoodsTable :money-crud="moneyCrud" :brands-kv="brandsKv" :dict="dict" />

                <MoneyForm :money-crud="moneyCrud" :rules="rules" :dialog-class="'!w-11/12 md:!w-9/12 !mt-6'">
                    <div :key="formRenderKey" class="w-full">
                        <template v-if="moneyCrud.form">
                            <GoodsBaseForm :form="moneyCrud.form" :brands="brands" :categories="categories" />

                            <GoodsPriceMatrix :form="moneyCrud.form" :dict="dict" />

                            <GoodsStatusForm :form="moneyCrud.form" :dict="dict" />
                        </template>
                    </div>
                </MoneyForm>
            </div>
        </div>
    </PageWrapper>
</template>

<script setup>
import { ref, watch } from "vue";
import MoneyCrud from '@/components/Crud/MoneyCrud.js';
import MoneyForm from "@/components/Crud/MoneyForm.vue";
import PageWrapper from "@/components/PageWrapper.vue";
import GoodsCategory from "@/views/gms/goods/GoodsCategory.vue";

import GoodsToolbar from "./components/GoodsToolbar.vue";
import GoodsTable from "./components/GoodsTable.vue";
import GoodsBaseForm from "./components/GoodsBaseForm.vue";
import GoodsPriceMatrix from "./components/GoodsPriceMatrix.vue";
import GoodsStatusForm from "./components/GoodsStatusForm.vue";

import { useUserStore } from "@/store/index.js";
import brandApi from "@/api/gms/brand.js";
import goodsApi from "@/api/gms/goods.js";
import dictApi from "@/api/system/dict.js";
import goodsCategoryApi from "@/api/gms/goodsCategory.js";
import NP from 'number-precision';
import { ElMessage } from 'element-plus';

const userStore = useUserStore();
const dict = ref({});
const brands = ref([]);
const brandsKv = ref({});
const categories = ref([]);
const formRenderKey = ref(Date.now());

// 🌟 核心审计拦截：精密处理价格矩阵载荷
const validatePayloadMatrix = (prices, coupons, isDouble, validLevels) => {
    const check = (source) => {
        const res = {};
        const allowed = (validLevels || []).map(l => String(l.value));
        for (const key of Object.keys(source || {})) {
            if (allowed.includes(key)) {
                const val = Number(source[key]);
                if (isNaN(val) || val < 0) throw new Error(`等级[${key}]的价格不合法，必须为非负数`);
                res[key] = NP.round(val, 2);
            }
        }
        return res;
    };
    return {
        levelPrices: check(prices),
        levelCoupons: isDouble ? check(coupons) : {}
    };
};

const hookedGoodsApi = {
    ...goodsApi,
    add: async (data) => {
        try {
            const payload = { ...data };
            // 只要开启过矩阵且有配置，就执行校验同步
            if (payload._validLevels && payload._validLevels.length > 0) {
                const { levelPrices, levelCoupons } = validatePayloadMatrix(
                    payload._matrixPrices, payload._matrixCoupons, payload._couponEnabled, payload._validLevels
                );
                payload.levelPrices = levelPrices;
                payload.levelCoupons = levelCoupons;
            }
            // 清理内部通信属性
            delete payload._matrixPrices; delete payload._matrixCoupons;
            delete payload._couponEnabled; delete payload._validLevels;
            return await goodsApi.add(payload);
        } catch (e) {
            ElMessage.error(e.message); return Promise.reject(e);
        }
    },
    edit: async (data) => {
        try {
            const payload = { ...data };
            // 只要有矩阵配置数据，就执行校验。不依赖 barcode 字段
            if (payload._validLevels && payload._validLevels.length > 0) {
                const { levelPrices, levelCoupons } = validatePayloadMatrix(
                    payload._matrixPrices, payload._matrixCoupons, payload._couponEnabled, payload._validLevels
                );
                payload.levelPrices = levelPrices;
                payload.levelCoupons = levelCoupons;
            }
            delete payload._matrixPrices; delete payload._matrixCoupons;
            delete payload._couponEnabled; delete payload._validLevels;
            return await goodsApi.edit(payload);
        } catch (e) {
            ElMessage.error(e.message); return Promise.reject(e);
        }
    }
};

const checkPrice = (rule, value, callback) => {
    const num = Number(value);
    if (isNaN(num) || num < 0) callback(new Error('必须为非负金额')); else callback();
};

const rules = {
    brandId: [{required: true, message: '请选择品牌'}],
    barcode: [{required: true, message: '请输入条码'}],
    name: [{required: true, message: '请输入名称'}],
    purchasePrice: [{required: true, message: '进价必填'}, {validator: checkPrice, trigger: 'blur'}],
    salePrice: [{required: true, message: '售价必填'}, {validator: checkPrice, trigger: 'blur'}],
    stock: [{required: true, message: '库存必填'}],
    status: [{required: true, message: '状态必填'}]
};

const moneyCrud = ref(new MoneyCrud({
    columns: [
        {prop: 'barcode', label: '条码', width: 140},
        {prop: 'name', label: '名称', minWidth: 140},
        {prop: 'status', label: '状态', width: 80},
        {prop: 'salePrice', label: '零售价', minWidth: 90, align: 'right'},
        {prop: 'purchasePrice', label: '成本', minWidth: 90, align: 'right'},
        {prop: 'stock', label: '库存', width: 80, align: 'center'},
        {prop: 'opt', label: '操作', width: 120, align: 'center', fixed: 'right', isMoneyUD: true },
    ],
    crudMethod: hookedGoodsApi,
    optShow: { add: true, edit: true, del: true, import: true, export: true },
    defaultForm: { status: 'SALE', stock: 0, isDiscountParticipable: 1 }
}));

watch(() => moneyCrud.value?.box, (isOpen) => { if (isOpen) formRenderKey.value = Date.now(); });

moneyCrud.value.init(moneyCrud, async () => {
    brands.value = await brandApi.getSelect().then(res => res.data);
    brands.value.forEach(e => { brandsKv.value[e.value] = e.label; });
    categories.value = await goodsCategoryApi.getSelect().then(res => res.data);
    dict.value = await dictApi.loadDict(["goodsStatus", "memberType"]);
});

function selectGoodsCategory(data) {
    moneyCrud.value.query.categoryId = data.id !== 0 ? data.id : null;
    moneyCrud.value.doQuery();
}
</script>