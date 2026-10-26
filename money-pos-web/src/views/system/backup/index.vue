<template>
    <div class="p-4 sm:p-6 lg:p-8 bg-gray-50 min-h-full">
        <div class="max-w-5xl mx-auto flex flex-col gap-6">
            <div>
                <h1 class="text-2xl font-black text-gray-800 flex items-center gap-2">
                    <el-icon class="text-blue-600"><DataAnalysis /></el-icon>
                    数据备份与恢复
                </h1>
                <p class="text-sm text-gray-500 mt-2 tracking-widest">
                    保障门店数据资产的最后一道防线。请定期进行数据备份，并妥善保管在 U 盘或云盘中。
                </p>
            </div>

            <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                <el-card shadow="hover" class="border-t-4 border-t-blue-500 rounded-lg">
                    <template #header>
                        <div class="flex items-center gap-2 font-bold text-lg text-blue-700">
                            <el-icon><Download /></el-icon> 一键全量备份
                        </div>
                    </template>
                    <div class="py-4 flex flex-col items-center text-center">
                        <div class="bg-blue-50 p-4 rounded-full mb-4">
                            <el-icon class="text-5xl text-blue-500"><Box /></el-icon>
                        </div>
                        <h3 class="font-bold text-gray-800 mb-2">创建完整备份</h3>
                        <p class="text-xs text-gray-500 mb-6 leading-relaxed px-4">
                            该操作将全量导出当前系统数据库与商品图片库，并打包为 ZIP 归档文件。
                            <strong class="text-blue-600">备份期间可继续收银</strong>，可随时执行。
                        </p>
                        <el-button type="primary" size="large" class="w-2/3 font-bold tracking-widest" :loading="backupLoading" @click="handleBackup">
                            <el-icon class="mr-2"><Download /></el-icon> 立即生成并下载备份
                        </el-button>
                    </div>
                </el-card>

                <el-card shadow="hover" class="border-t-4 border-t-red-500 rounded-lg bg-red-50/30">
                    <template #header>
                        <div class="flex items-center gap-2 font-bold text-lg text-red-600">
                            <el-icon><RefreshLeft /></el-icon> 从备份恢复
                        </div>
                    </template>
                    <div class="py-4 flex flex-col items-center text-center">
                        <div class="bg-red-100 p-4 rounded-full mb-4">
                            <el-icon class="text-5xl text-red-500"><WarnTriangleFilled /></el-icon>
                        </div>
                        <h3 class="font-bold text-gray-800 mb-2">恢复本机数据</h3>
                        <p class="text-xs text-red-500 mb-6 leading-relaxed px-4 font-bold">
                            ⚠️ 恢复会覆盖当前电脑上的商品、订单和会员资料，请确认已选择正确的备份文件。
                        </p>

                        <el-upload action="#" :show-file-list="false" :http-request="handleRestore" accept=".zip" :disabled="restoreLoading">
                            <el-button type="danger" size="large" class="w-full font-bold tracking-widest px-8 shadow-md" :loading="restoreLoading">
                                <el-icon class="mr-2"><Upload /></el-icon> 选择备份文件并恢复
                            </el-button>
                        </el-upload>
                    </div>
                </el-card>
            </div>

            <el-card shadow="never" class="bg-[#1e1e1e] border-0 rounded-lg overflow-hidden" :body-style="{ padding: '0px' }">
                <div class="bg-gray-800 px-4 py-2 flex items-center justify-between border-b border-gray-700">
                    <span class="text-gray-300 font-mono text-sm flex items-center gap-2">
                        <el-icon class="animate-pulse text-green-500"><Monitor /></el-icon> 恢复进度
                    </span>
                    <el-button link size="small" type="info" @click="consoleLogs = []">清空记录</el-button>
                </div>

                <div class="h-64 overflow-y-auto p-4 font-mono text-sm leading-relaxed" ref="consoleRef" id="consoleBox">
                    <div v-if="consoleLogs.length === 0" class="text-gray-600 italic">等待操作…</div>
                    <div v-for="(log, idx) in consoleLogs" :key="idx" class="flex gap-3 mb-1.5 transition-all">
                        <span class="text-gray-500 shrink-0">[{{ log.time }}]</span>
                        <span :class="{
                            'text-green-400': log.level === 'SUCCESS',
                            'text-blue-400': log.level === 'INFO',
                            'text-yellow-400': log.level === 'WARN',
                            'text-red-500 font-bold': log.level === 'ERROR'
                        }">{{ log.msg }}</span>
                    </div>
                    <div v-if="restoreLoading" class="text-green-500 animate-pulse mt-1">_</div>
                </div>
            </el-card>

        </div>
    </div>
</template>

<script setup>
import { ref, nextTick, onMounted, onUnmounted } from 'vue';
import { req } from '@/api/index.js'; // 确保路径正确
import { getApiBaseUrl } from '@/api/axios.js';
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus';
import { DataAnalysis, Download, Box, RefreshLeft, WarnTriangleFilled, Upload, Monitor } from '@element-plus/icons-vue';

const backupLoading = ref(false);
const restoreLoading = ref(false);

const consoleLogs = ref([]);
const consoleRef = ref(null);
let sseSource = null;

// ==========================================
// 开启 SSE 实时监听通道，并复用 Axios 已确定的运行时基址。
// ==========================================
const initSseListener = async () => {
    const baseUrl = getApiBaseUrl();
    const ticketResponse = await req({ url: '/sys/backup/stream-ticket', method: 'post' });
    const ticket = ticketResponse?.data?.ticket;
    if (!ticket) {
        throw new Error('未能获取备份日志连接凭据');
    }
    const sseUrl = `${baseUrl.replace(/\/$/, '')}/sys/backup/stream?ticket=${encodeURIComponent(ticket)}`;

    sseSource = new EventSource(sseUrl);

    sseSource.onmessage = (event) => {
        try {
            const data = JSON.parse(event.data);
            consoleLogs.value.push(data);

            // 自动滚动到底部
            nextTick(() => {
                if (consoleRef.value) {
                    consoleRef.value.scrollTop = consoleRef.value.scrollHeight;
                }
            });
        } catch(e) {}
    };

    sseSource.onerror = (err) => {
        // 由于 SSE 在连接建立前如果遇到代理 404/500 会触发 onerror，
        // 这里可以作为调试信息保留，不用特殊处理，浏览器会自动重试。
        console.error("SSE 大屏连接遇到阻断:", err);
    };
};

onMounted(() => {
    initSseListener().catch((error) => console.error('初始化备份日志连接失败:', error));
});

onUnmounted(() => {
    if (sseSource) sseSource.close();
});

// ==========================================
// 🌟 执行一键备份 (流式下载处理)
// ==========================================
const handleBackup = async () => {
    backupLoading.value = true;
    const notification = ElNotification({
        title: '正在生成备份...',
        message: '正在准备备份文件，请勿关闭页面，这可能需要几十秒。',
        type: 'info',
        duration: 0,
        showClose: false
    });

    try {
        const res = await req({
            url: '/sys/backup/export',
            method: 'GET',
            responseType: 'blob',
            timeout: 600000
        });

        let fileName = `MoneyPOS_备份_${new Date().getTime()}.zip`;
        const disposition = res.headers ? res.headers['content-disposition'] : '';
        if (disposition && disposition.includes('filename=')) {
            const match = disposition.match(/filename="?([^"]+)"?/);
            if (match && match[1]) {
                fileName = decodeURIComponent(match[1]);
            }
        }

        const blob = new Blob([res.data || res], { type: 'application/zip' });
        const downloadUrl = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.style.display = 'none';
        link.href = downloadUrl;
        link.download = fileName;
        document.body.appendChild(link);
        link.click();

        window.URL.revokeObjectURL(downloadUrl);
        document.body.removeChild(link);

        notification.close();
        ElMessage.success("✅ 备份包生成并下载成功！请妥善保管！");

    } catch (error) {
        console.error("备份失败:", error);
        notification.close();
        ElMessage.error("备份失败，请检查磁盘空间后重试");
    } finally {
        backupLoading.value = false;
    }
};

// ==========================================
// 🌟 执行灾难还原 (带有二次确认与大屏联动)
// ==========================================
const handleRestore = async (options) => {
    const file = options.file;
    if (!file.name.toLowerCase().endsWith('.zip')) {
        return ElMessage.warning("请选择 ZIP 格式的备份文件");
    }

    try {
        await ElMessageBox.confirm(
            `<div style="color: #dc2626; font-weight: bold; margin-bottom: 10px;">请确认恢复操作</div>
             即将使用 <b>${file.name}</b> 覆盖当前本机资料。<br>
             当前电脑上的商品、订单和会员资料将被备份内容替换，且无法撤销。<br>
             确认继续恢复吗？`,
            '确认从备份恢复',
            {
                dangerouslyUseHTMLString: true,
                confirmButtonText: '确认并继续',
                cancelButtonText: '取消',
                confirmButtonClass: 'el-button--danger',
                type: 'error',
            }
        );

        const { value } = await ElMessageBox.prompt(
            '为防止误触，请在下方输入框中输入 "<b>确认还原</b>" 四个字：',
            '最终确认',
            {
                dangerouslyUseHTMLString: true,
                confirmButtonText: '执行',
                cancelButtonText: '取消',
                inputValidator: (val) => {
                    return val === '确认还原' ? true : '验证口令不正确';
                }
            }
        );

        if (value === '确认还原') {
            executeRestoreApi(file);
        }

    } catch (e) {
        if (e !== 'cancel') {
            console.error(e);
        } else {
            ElMessage.info("已取消恢复操作");
        }
    }
};

const executeRestoreApi = async (file) => {
    consoleLogs.value = []; // 清理上一波旧屏幕日志
    restoreLoading.value = true;
    const notification = ElNotification({
        title: '正在恢复数据…',
        message: '请关注下方恢复进度；完成前请勿关闭软件或断电。',
        type: 'warning',
        duration: 0,
        showClose: false
    });

    try {
        const formData = new FormData();
        formData.append('file', file);

        const res = await req({
            url: '/sys/backup/restore',
            method: 'POST',
            data: formData,
            headers: { 'Content-Type': 'multipart/form-data' },
            timeout: 600000
        });

        notification.close();

        // 接收到后端的 205 状态码，弹窗提示重启
        if (res.code === 205 || res.data?.code === 205) {
            ElMessageBox.alert(
                '数据恢复完成。请关闭并重新打开本软件，以载入恢复后的资料。',
                '恢复完成，请重新启动',
                {
                    confirmButtonText: '我知道了',
                    type: 'success',
                    callback: () => {
                        window.location.reload();
                    }
                }
            );
        } else {
            ElMessage.error("恢复未完成，请查看恢复进度后重试");
        }

    } catch (error) {
        console.error("还原失败:", error);
        notification.close();
        ElMessage.error("恢复未完成，请查看恢复进度后重试");
    } finally {
        restoreLoading.value = false;
    }
};
</script>
