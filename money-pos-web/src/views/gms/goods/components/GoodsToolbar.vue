<template>
    <div class="flex flex-col gap-4">
        <MoneyRR :money-crud="moneyCrud">
            <SmartGoodsSelector
                class="w-full md:!w-[350px]" size="default" placeholder="智能搜商品(支持名称/条码/拼音)"
                @select="handleGoodsSelect" @clear="handleGoodsClear"
            />
            <el-select v-model="moneyCrud.query.brandId" clearable class="w-full md:!w-48" placeholder="品牌" @change="moneyCrud.doQuery">
                <el-option v-for="item in brands" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-select v-model="moneyCrud.query.categoryId" clearable class="w-full md:!w-48 md:!hidden" placeholder="分类" @change="moneyCrud.doQuery">
                <el-option v-for="item in categories" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-select v-model="moneyCrud.query.status" clearable placeholder="状态" class="md:!w-48" @change="moneyCrud.doQuery">
                <el-option v-for="item in dict.goodsStatus" :key="item.value" :label="item.desc" :value="item.value" />
            </el-select>
        </MoneyRR>

        <div class="flex items-center gap-2">
            <MoneyCUD :money-crud="moneyCrud" />

            <el-button type="warning" icon="Download" plain v-if="moneyCrud.optShow.export" @click="handleDownloadTemplate">模板下载</el-button>

            <el-upload
                action="" :http-request="handleImport" :show-file-list="false" accept=".xls,.xlsx"
                class="inline-block" v-if="moneyCrud.optShow.import"
            >
                <el-button type="success" icon="Upload" plain :loading="importing">批量导入</el-button>
            </el-upload>

            <el-button type="primary" icon="Download" plain @click="handleExportGoods">一键导出</el-button>
        </div>
    </div>
</template>

<script setup>
import MoneyRR from "@/components/Crud/MoneyRR.vue";
import MoneyCUD from "@/components/Crud/MoneyCUD.vue";
import SmartGoodsSelector from "@/components/common/SmartGoodsSelector.vue";
import { useGoodsImportExport } from '../composables/useGoodsImportExport.js';
import { ElMessage } from "element-plus";
import { req } from '@/api/index.js';

const props = defineProps({
    moneyCrud: Object, brands: Array, categories: Array, dict: Object
});

const { importing, handleDownloadTemplate, handleImport } = useGoodsImportExport(() => props.moneyCrud.doQuery());

const handleGoodsSelect = (goods) => {
    props.moneyCrud.query.barcode = goods.barcode;
    props.moneyCrud.query.name = null;
    props.moneyCrud.doQuery();
};

const handleGoodsClear = () => {
    props.moneyCrud.query.barcode = null;
    props.moneyCrud.query.name = null;
    props.moneyCrud.doQuery();
};

const handleExportGoods = async () => {
    ElMessage.success("正在生成 Excel 数据，请稍候...");
    try {
        const res = await req({
            url: '/gms/goods/export',
            method: 'GET',
            responseType: 'blob'
        });

        const blob = new Blob([res.data || res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
        const downloadUrl = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.style.display = 'none';
        link.href = downloadUrl;
        link.download = `门店商品全量档案_${new Date().getTime()}.xlsx`;
        document.body.appendChild(link);
        link.click();
        window.URL.revokeObjectURL(downloadUrl);
        document.body.removeChild(link);

        ElMessage.success("✅ 导出成功！");
    } catch (e) {
        console.error("导出异常:", e);
        ElMessage.error("导出失败，请检查网络！");
    }
};
</script>