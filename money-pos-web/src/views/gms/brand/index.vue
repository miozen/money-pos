<template>
    <PageWrapper>
        <MoneyRR :money-crud="moneyCrud">
            <el-input v-model="moneyCrud.query.name" placeholder="品牌名称" class="md:!w-48"
                      @keyup.enter.native="moneyCrud.doQuery" />
        </MoneyRR>

        <MoneyCUD :money-crud="moneyCrud" />

        <MoneyCrudTable :money-crud="moneyCrud">
            <template #logo="{scope}">
                <el-image
                    class="w-8 h-8"
                    preview-teleported
                    :src="$money.getOssUrl(scope.row.logo)"
                    :preview-src-list="[$money.getOssUrl(scope.row.logo)]"
                    fit="cover"
                />
            </template>
            <template #opt="{scope}">
                <el-button type="warning" plain size="small" @click="openConfig(scope.row)">
                    <el-icon class="mr-1"><Setting /></el-icon> 定价策略
                </el-button>
                <MoneyUD :money-crud="moneyCrud" :scope="scope" />
            </template>
        </MoneyCrudTable>

        <MoneyForm :money-crud="moneyCrud" :rules="rules">
            <el-form-item label="Logo" prop="logo">
                <el-upload class="avatar-uploader" :auto-upload="false" :show-file-list="false" accept="image/*"
                           :on-change="handleLogoSuccess">
                    <img v-if="moneyCrud.form.logo" :src="$money.getOssUrl(moneyCrud.form.logo)" class="w-24" alt="logo">
                    <el-icon v-else class="avatar-uploader-icon !w-24 !h-24"><Plus /></el-icon>
                </el-upload>
            </el-form-item>
            <el-form-item label="名称" prop="name">
                <el-input v-model.trim="moneyCrud.form.name" />
            </el-form-item>
            <el-form-item label="描述">
                <el-input v-model.trim="moneyCrud.form.description" type="textarea" maxlength="250" show-word-limit />
            </el-form-item>
        </MoneyForm>

        <el-dialog v-model="configVisible" :title="`⚙️ 定价策略配置 - ${currentBrandName}`" width="550px" destroy-on-close>
            <div class="bg-blue-50 border border-blue-100 p-4 rounded-lg mb-5 text-sm text-gray-600">
                在此处决定该品牌在【商品录入】和【收银台】的底层计算法则与等级开放权限。
            </div>

            <el-form :model="configForm" label-width="140px" v-loading="configLoading">
                <el-form-item label="会员券双轨模式">
                    <el-switch v-model="configForm.couponEnabled" active-text="开启 (绿叶模式)" inactive-text="关闭 (纯价模式)" inline-prompt style="--el-switch-on-color: #10b981;" />
                    <div class="w-full text-xs text-gray-400 mt-1">
                        开启后，该品牌商品强制执行 <span class="text-green-600 font-bold">售价 = 会员价 + 会员券</span> 公式。
                    </div>
                </el-form-item>

                <el-form-item label="开放等级 (字典)" class="mt-6">
                    <el-checkbox-group v-model="configForm.levelCodesArray" class="flex flex-col gap-2">
                        <el-checkbox v-for="item in memberTypes" :key="item.value" :label="item.value" border>
                            {{ item.desc }} <span class="text-gray-300 text-xs ml-2">({{ item.value }})</span>
                        </el-checkbox>
                    </el-checkbox-group>
                    <div class="w-full text-xs text-gray-400 mt-2">
                        勾选后，该品牌在商品编辑页将只展示选中的这几个专属等级定价框。
                    </div>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="configVisible = false">取消</el-button>
                <el-button type="primary" @click="saveConfig" :loading="configSaving" class="font-bold tracking-widest px-8">保存策略配置</el-button>
            </template>
        </el-dialog>

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
import { useUserStore } from "@/store/index.js";
import brandApi from "@/api/gms/brand.js"
import dictApi from "@/api/system/dict.js"
import { req } from '@/api/index.js'
import { Setting, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const userStore = useUserStore()

// ==================== 1. 先声明基础 CRUD 逻辑 ====================
const columns = [
    {prop: 'name', label: '品牌名称'},
    {prop: 'logo', label: 'Logo', show: false},
    {prop: 'description', label: '描述'},
    {prop: 'goodsCount', label: '商品数量'},
    {prop: 'opt', label: '操作', width: 220, align: 'center', fixed: 'right', showOverflowTooltip: false, isMoneyUD: true},
]
const rules = { name: [{required: true, message: '请输入品牌名称'}] }

// 🌟 必须先把 moneyCrud 声明出来！
const moneyCrud = ref(new MoneyCrud({
    columns,
    crudMethod: brandApi,
    optShow: {
        checkbox: userStore.hasPermission(['gmsBrand:edit', 'gmsBrand:del']),
        add: userStore.hasPermission('gmsBrand:add'),
        edit: userStore.hasPermission('gmsBrand:edit'),
        del: userStore.hasPermission('gmsBrand:del')
    }
}))

// ==================== 2. 再声明策略面板逻辑 ====================
const memberTypes = ref([])
const configVisible = ref(false)
const configLoading = ref(false)
const configSaving = ref(false)
const currentBrandName = ref('')
const configForm = ref({ brandId: null, couponEnabled: false, levelCodesArray: [] })

// 🌟 最后执行初始化！
moneyCrud.value.init(moneyCrud, async () => {
    const dictRes = await dictApi.loadDict(["memberType"])
    // 过滤掉基础的 MEMBER，因为系统级默认零售不在这里配
    memberTypes.value = (dictRes.memberType || []).filter(item => item.value !== 'MEMBER')
})

function handleLogoSuccess(file) {
    moneyCrud.value.form.logo = URL.createObjectURL(file.raw)
    moneyCrud.value.form.logoFile = file.raw
}

// 点击“定价策略”按钮
async function openConfig(row) {
    currentBrandName.value = row.name
    configForm.value.brandId = row.id
    configForm.value.couponEnabled = false
    configForm.value.levelCodesArray = []

    configVisible.value = true
    configLoading.value = true

    try {
        // 去后端拉取真实的配置
        const res = await req({ url: '/gms/brand/config', method: 'GET', params: { brandId: row.id } })
        if (res && res.data) {
            configForm.value.couponEnabled = res.data.couponEnabled || false
            configForm.value.levelCodesArray = res.data.levelCodes || []
        }
    } catch (e) {
        ElMessage.error('拉取策略失败，请检查网络')
    } finally {
        configLoading.value = false
    }
}

// 提交保存配置
async function saveConfig() {
    configSaving.value = true
    try {
        const payload = {
            brand: configForm.value.brandId.toString(),
            couponEnabled: configForm.value.couponEnabled,
            // 后端数据库存的是逗号分隔的字符串
            levelCodes: configForm.value.levelCodesArray.join(',')
        }
        await req({ url: '/gms/brand/config', method: 'POST', data: payload })
        ElMessage.success('🎉 品牌策略部署成功！')
        configVisible.value = false
    } catch (e) {
        ElMessage.error('保存失败')
    } finally {
        configSaving.value = false
    }
}
</script>