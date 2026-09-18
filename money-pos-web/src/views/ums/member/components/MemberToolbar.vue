<template>
    <div class="flex flex-col gap-4">
        <MoneyRR :money-crud="moneyCrud">
            <MemberSmartSearch
                class="w-[350px] md:!w-[420px]"
                size="default"
                placeholder="快速定位会员 (支持名/号/手机模糊查)"
                @select="handleSelect"
                @clear="handleClear"
            />
        </MoneyRR>

        <div class="flex items-center gap-2">
            <MoneyCUD :money-crud="moneyCrud" />

            <el-button type="info" icon="Download" plain @click="handleDownloadTemplate">下载模板</el-button>

            <el-upload action="#" :http-request="handleImport" :show-file-list="false" accept=".xlsx, .xls">
                <el-button type="success" icon="Upload" plain :loading="importLoading">导入老会员</el-button>
            </el-upload>

            <el-button type="primary" icon="Download" plain @click="handleExportMembers">一键导出资产</el-button>
        </div>
    </div>
</template>

<script setup>
import MoneyRR from "@/components/Crud/MoneyRR.vue";
import MoneyCUD from "@/components/Crud/MoneyCUD.vue";
import MemberSmartSearch from "@/components/common/MemberSmartSearch.vue";
import { useDataImportExport } from "@/hooks/useDataImportExport.js";
import memberApi from "@/api/ums/member.js";
import { req } from '@/api/index.js'; // 🌟 引入核心请求工具
import { ElMessage } from "element-plus";

const props = defineProps({ moneyCrud: Object });

const { importLoading, handleDownloadTemplate, handleImport } = useDataImportExport({
    downloadApi: memberApi.downloadTemplate,
    importApi: memberApi.importMembers,
    onSuccess: () => props.moneyCrud.doQuery(),
    fileName: '会员导入模板.xlsx'
});

const handleSelect = (m) => { props.moneyCrud.query.code = m.code; props.moneyCrud.doQuery(); };
const handleClear = () => { props.moneyCrud.query.code = null; props.moneyCrud.doQuery(); };

// ==========================================
// 🌟 核心功能：一键导出老会员资产大表 (Blob流安全下载)
// ==========================================
const handleExportMembers = async () => {
    ElMessage.success("正在生成会员资产大表，请稍候...");
    try {
        // 1. 发起请求，必须使用 blob 类型以接收文件流
        const res = await req({
            url: '/ums/member/export', // 刚刚在后端写的接口地址
            method: 'GET',
            responseType: 'blob'
        });

        // 2. 转换为 Excel 文件 Blob
        const blob = new Blob([res.data || res], {
            type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
        });

        // 3. 模拟点击原生 <a> 标签触发下载
        const downloadUrl = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.style.display = 'none';
        link.href = downloadUrl;

        // 动态生成包含时间戳的文件名，防重名
        const timestamp = new Date().toISOString().replace(/[-:T]/g, '').slice(0, 14);
        link.download = `门店全量会员资产大表_${timestamp}.xlsx`;

        document.body.appendChild(link);
        link.click();

        // 4. 打扫战场释放内存
        window.URL.revokeObjectURL(downloadUrl);
        document.body.removeChild(link);

        ElMessage.success("✅ 会员大表导出成功！");
    } catch (e) {
        console.error("导出会员异常:", e);
        ElMessage.error("导出失败，请检查网络或后端日志！");
    }
};
</script>