<template>
    <PageWrapper>
        <div class="flex flex-col lg:flex-row gap-6">
            <div class="flex-1">
                <el-card shadow="never" class="border-none shadow-sm rounded-xl h-full">
                    <template #header>
                        <div class="flex items-center gap-2 font-bold text-gray-800 text-lg">
                            <el-icon class="text-blue-500"><Monitor /></el-icon> 顾客副屏设置
                        </div>
                    </template>
                    <el-form :model="settings" label-position="top" class="mt-2" v-loading="loading">
                        <div class="bg-blue-50/50 p-4 rounded-lg border border-blue-100 mb-6 flex justify-between items-center">
                            <div>
                                <h4 class="font-bold text-blue-800 text-base">启用顾客副屏</h4>
                                <p class="text-xs text-gray-500 mt-1">关闭后，顾客副屏将停止显示收银信息。</p>
                            </div>
                            <el-switch v-model="settings.enabled" active-text="启动" inactive-text="停用" inline-prompt style="--el-switch-on-color: #3b82f6;" />
                        </div>
                        <div :class="{'opacity-50 pointer-events-none grayscale transition-all': !settings.enabled}">
                            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                                <el-form-item>
                                    <template #label><span class="font-bold text-gray-700 flex items-center gap-1"><el-icon><Timer /></el-icon> 海报轮播间隔 (秒)</span></template>
                                    <el-input-number v-model="settings.interval" :min="3" :max="60" class="!w-full" controls-position="right" />
                                </el-form-item>
                                <el-form-item>
                                    <template #label><span class="font-bold text-gray-700 flex items-center gap-1"><el-icon><ChatDotRound /></el-icon> 底部滚动公告</span></template>
                                    <el-input v-model="settings.welcomeText" placeholder="请输入副屏底部显示的滚动文字..." clearable />
                                </el-form-item>
                            </div>
                            <div class="mt-4 mb-6">
                                <div class="font-bold text-gray-700 flex items-center gap-1 mb-3"><el-icon class="text-green-500"><VideoPlay /></el-icon> 待机海报轮播</div>
                                <div class="bg-gray-50 p-4 rounded-lg border border-gray-200 min-h-[120px] flex flex-wrap gap-4 items-center">
                                    <div v-if="settings.playlist.length === 0" class="text-gray-400 text-sm w-full text-center py-4">暂无播放内容 👇</div>
                                    <div v-for="(url, index) in settings.playlist" :key="'play_'+index" class="relative group cursor-pointer">
                                        <div class="absolute -top-2 -left-2 w-6 h-6 bg-green-500 text-white rounded-full flex items-center justify-center text-xs font-black z-10 shadow">{{ index + 1 }}</div>
                                        <el-image :src="url" class="w-24 h-16 rounded shadow-sm border border-gray-300 object-cover" />
                                        <div @click="removeFromPlaylist(index)" class="absolute inset-0 bg-red-500/80 text-white opacity-0 group-hover:opacity-100 transition-opacity rounded flex flex-col items-center justify-center"><el-icon><Delete /></el-icon></div>
                                    </div>
                                </div>
                            </div>
                            <div class="mb-8">
                                <div class="font-bold text-gray-700 flex items-center gap-1 mb-3"><el-icon class="text-orange-500"><PictureRounded /></el-icon> 门店海报素材库</div>
                                <div class="flex flex-wrap gap-3 items-start">
                                    <div v-for="(url, index) in settings.library" :key="'lib_'+index" class="relative group cursor-pointer border-2 border-transparent hover:border-blue-400 rounded transition-all p-1">
                                        <el-image :src="url" class="w-16 h-16 rounded object-cover shadow-sm border border-gray-200" />
                                        <div v-if="settings.playlist.includes(url)" class="absolute top-1 right-1 text-green-500 bg-white rounded-full flex"><el-icon><SuccessFilled /></el-icon></div>
                                        <div v-else @click="addToPlaylist(url)" class="absolute inset-1 bg-blue-500/80 text-white opacity-0 group-hover:opacity-100 transition-opacity rounded flex flex-col items-center justify-center"><el-icon><Plus /></el-icon></div>
                                        <div @click.stop="deleteFromLibrary(index)" class="absolute -top-2 -right-2 w-5 h-5 bg-gray-500 hover:bg-red-500 text-white rounded-full flex items-center justify-center text-xs opacity-0 group-hover:opacity-100 z-20"><el-icon><Close /></el-icon></div>
                                    </div>
                                    <el-upload :action="uploadUrl" :headers="headers" :show-file-list="false" :before-upload="handleBeforeUpload" :on-success="handleUploadSuccess" accept=".jpg,.png,.webp" class="w-16 h-16">
                                        <div class="w-16 h-16 border-2 border-dashed border-gray-300 rounded hover:border-blue-500 flex flex-col items-center justify-center text-gray-400"><el-icon class="text-xl"><UploadFilled /></el-icon></div>
                                    </el-upload>
                                </div>
                            </div>
                            <div class="mt-8 pt-6 border-t border-gray-100">
                                <div class="flex justify-between items-end mb-4">
                                    <div class="font-bold text-gray-700 flex items-center gap-1"><el-icon class="text-blue-600"><CreditCard /></el-icon> 收款码展示</div>
                                    <div class="text-[10px] text-gray-400">勾选“设为默认”的收款码，结算时将优先显示</div>
                                </div>
                                <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                                    <div v-for="(item, idx) in settings.paymentCodes" :key="idx" class="border border-gray-200 rounded-lg p-3 bg-gray-50 relative group">
                                        <el-icon @click="removePaymentCode(idx)" class="absolute top-2 right-2 cursor-pointer text-gray-300 hover:text-red-500"><Close /></el-icon>
                                        <el-input v-model="item.name" size="small" placeholder="渠道名：如 农行聚合" class="mb-3 w-4/5" />
                                        <div class="flex items-center gap-3">
                                            <el-upload :action="uploadUrl" :headers="headers" :show-file-list="false" :on-success="(res) => handleQrUploadSuccess(res, idx)" accept=".jpg,.png" class="shrink-0 w-16 h-16 border border-dashed rounded flex items-center justify-center hover:border-blue-500 bg-white overflow-hidden">
                                                <img v-if="item.url" :src="item.url" class="w-full h-full object-cover" />
                                                <el-icon v-else class="text-xl text-gray-400"><Plus /></el-icon>
                                            </el-upload>
                                            <div class="flex flex-col gap-1"><el-switch v-model="item.isDefault" active-text="设为默认" size="small" /></div>
                                        </div>
                                    </div>
                                    <div @click="addPaymentCode" class="border-2 border-dashed rounded-lg flex flex-col items-center justify-center text-gray-400 hover:text-blue-500 cursor-pointer min-h-[110px] bg-white">
                                        <el-icon class="text-2xl"><Plus /></el-icon><span class="text-xs mt-2 font-bold">新增收款通道</span>
                                    </div>
                                </div>
                            </div>
                            <div class="mt-10 pt-6 border-t border-gray-100 flex items-center gap-4">
                                <el-button type="primary" size="large" class="w-full md:w-64 font-bold tracking-widest shadow-lg" @click="saveSettings" :loading="saving"><el-icon class="mr-1"><Select /></el-icon> 保存副屏设置</el-button>
                            </div>
                        </div>
                    </el-form>
                </el-card>
            </div>

            <div class="w-full lg:w-[450px] shrink-0">
                <PosStateMirror
                    :settings="settings"
                    :posState="posState"
                    :realCart="realCart"
                    :realMember="realMember"
                    :realParticipatingAmount="realParticipatingAmount"
                    :realPayment="realPayment"
                    :getPrice="getPrice"
                    :getOriginalPrice="getOriginalPrice"
                    :getSubtotal="getSubtotal"
                    :getQty="getQty"
                />
            </div>
        </div>
    </PageWrapper>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import PageWrapper from "@/components/PageWrapper.vue"
import { Monitor, Timer, PictureRounded, Select, VideoPlay, Delete, Plus, SuccessFilled, UploadFilled, Close, ChatDotRound, CreditCard } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import { getToken } from '@/composables/token.js'
import PosStateMirror from './components/PosStateMirror.vue'
import { usePosSync } from '@/composables/usePosSync.js'

const displayChannel = new BroadcastChannel('pos_display_channel')
const loading = ref(false)
const saving = ref(false)

let apiBaseUrl = import.meta.env.VITE_BASE_URL;
if (window.location.protocol === 'file:') { apiBaseUrl = 'http://127.0.0.1:9101/money-pos'; }

const uploadUrl = ref(apiBaseUrl + '/common/upload')
const headers = computed(() => ({ Authorization: 'Bearer ' + getToken() }))
const settings = reactive({ enabled: true, interval: 5, welcomeText: '欢迎光临！今日全场满99减20...', paymentCodes: [], library: [], playlist: [] })

// 🌟 核心：一键拉取封装好的通讯与状态逻辑！
const {
    posState, realCart, realMember, realParticipatingAmount, realPayment,
    initReceiver, closeReceiver, getPrice, getOriginalPrice, getSubtotal, getQty
} = usePosSync()

const addToPlaylist = (url) => { if (!settings.playlist.includes(url)) settings.playlist.push(url); }
const removeFromPlaylist = (index) => settings.playlist.splice(index, 1);
const deleteFromLibrary = (index) => {
    ElMessageBox.confirm('彻底删除素材？', '警告', { type: 'warning' }).then(() => {
        const url = settings.library[index];
        settings.library.splice(index, 1);
        const pIdx = settings.playlist.indexOf(url);
        if (pIdx > -1) settings.playlist.splice(pIdx, 1);
    }).catch(() => {});
}
const addPaymentCode = () => settings.paymentCodes.push({ name: '', url: '', isDefault: false });
const removePaymentCode = (index) => settings.paymentCodes.splice(index, 1);

// 🌟 依然保留之前的参数对齐修复版
const handleQrUploadSuccess = (response, index) => {
    const url = response?.url || response?.data?.url || response;
    if (url && typeof url === 'string') {
        if (!settings.paymentCodes[index]) settings.paymentCodes[index] = { name: '', url: '', isDefault: false };
        settings.paymentCodes[index].url = url;
        ElMessage.success('收款码已上传');
    } else {
         ElMessage.error('上传失败，请重试');
    }
}

const handleBeforeUpload = (file) => file.size / 1024 / 1024 < 10;
const handleUploadSuccess = (response, uploadFile) => {
    const url = response?.url || response?.data?.url || response;
    if (url && typeof url === 'string') {
        settings.library.unshift(url);
        ElMessage.success('素材上传成功！');
    } else {
        ElMessage.error('上传失败，请重试');
    }
}

const fetchSettings = async () => {
    loading.value = true;
    try {
        const res = await axios.get(`${apiBaseUrl}/common/display-settings`, { headers: headers.value });
        const rawData = res.data.data || res.data;
        if (rawData && rawData !== "{}") {
            const data = typeof rawData === 'string' ? JSON.parse(rawData) : rawData;
            if (!data.paymentCodes) data.paymentCodes = [];
            Object.assign(settings, data);
        }
    } catch (e) {
    } finally {
        loading.value = false;
    }
}

const saveSettings = async () => {
    saving.value = true;
    try {
        await axios.put(`${apiBaseUrl}/common/display-settings`, settings, { headers: headers.value });
        ElMessage.success('副屏设置已保存');
        displayChannel.postMessage('RELOAD_SETTINGS');
    } catch (e) {
        ElMessage.error('保存失败');
    } finally {
        saving.value = false;
    }
}

onMounted(() => { fetchSettings(); initReceiver(); })
onUnmounted(() => { closeReceiver(); })
</script>
