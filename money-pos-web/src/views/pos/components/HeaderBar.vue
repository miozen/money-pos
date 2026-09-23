<template>
    <div class="h-16 bg-gray-900 text-white flex justify-between items-center px-6 shadow-md select-none shrink-0 z-10 border-b border-gray-800">
        <div class="flex items-center gap-3">
            <el-icon style="font-size: 44px;" class="text-blue-500"><Monitor /></el-icon>
            <div class="flex flex-col justify-center">
                <span class="text-xl font-black tracking-wider bg-clip-text text-transparent bg-gradient-to-r from-blue-400 to-cyan-300 leading-tight">
                    门店极速收银
                </span>
                <span class="text-[10px] text-gray-500 tracking-widest uppercase mt-0.5">Professional POS System</span>
            </div>
        </div>

        <div class="flex items-center gap-6">
            <div class="nav-item group" @click="$emit('action', 'sales')">
                <el-icon class="text-xl"><Document /></el-icon><span>销售单</span>
            </div>
            <div class="nav-item group" @click="$emit('action', 'shift')">
                <el-icon class="text-xl"><Refresh /></el-icon><span>交接班</span>
            </div>
            <div class="nav-item group" @click="$emit('action', 'addMember')">
                <el-icon class="text-xl"><User /></el-icon><span>新增会员</span>
            </div>
            <div class="nav-item group" @click="$emit('action', 'recharge')">
                <el-icon class="text-xl"><Coin /></el-icon><span>会员充值</span>
            </div>
            <div class="nav-item group" @click="$emit('action', 'restock')">
                <el-icon class="text-xl"><Goods /></el-icon><span>极速补货</span>
            </div>
        </div>

        <div class="flex items-center gap-5">
            <div class="flex items-center gap-1 cursor-pointer text-orange-400 hover:text-orange-300 transition-colors bg-gray-800 px-3 py-1.5 rounded-full border border-gray-700" @click="$emit('action', 'admin')">
                <el-icon><HomeFilled /></el-icon>
                <span class="text-sm font-bold">后台管理</span>
            </div>
            <div class="flex items-center gap-1.5 cursor-pointer text-gray-300 hover:text-blue-300 transition-colors" title="切换收银员" @click="$emit('action', 'switchCashier')">
                <el-icon><Avatar /></el-icon>
                <span class="text-sm font-bold">{{ cashierName }}</span>
                <el-icon class="text-xs"><SwitchButton /></el-icon>
                <span class="text-xs font-bold">切换</span>
            </div>
            <div class="text-xl font-black text-cyan-400 tracking-widest w-24 text-right">
                {{ currentTime.split(' ')[1] }}
            </div>
        </div>
    </div>
</template>

<script setup>
import { Monitor, Document, Refresh, User, Coin, Goods, HomeFilled, Avatar, SwitchButton } from '@element-plus/icons-vue'
defineProps(['cashierName', 'currentTime'])
defineEmits(['action'])
</script>

<style scoped>
.nav-item {
    /* 增加 font-bold 统一加粗 */
    @apply flex items-center gap-1.5 cursor-pointer text-sm font-bold text-gray-300 hover:text-blue-400 transition-colors duration-200;
}
</style>
