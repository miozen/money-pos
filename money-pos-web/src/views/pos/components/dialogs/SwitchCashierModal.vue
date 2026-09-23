<template>
    <el-dialog
        v-model="visible"
        title="切换收银员"
        width="420px"
        destroy-on-close
        :close-on-click-modal="false"
        @open="loadCandidates"
        @closed="handleClosed"
    >
        <p class="text-sm text-gray-500 mb-5">请输入下一位收银员的密码。当前购物车必须为空。</p>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
            <el-form-item label="收银员" prop="username">
                <el-select v-model="form.username" class="w-full" placeholder="请选择收银员" :loading="candidateLoading">
                    <el-option
                        v-for="candidate in candidates"
                        :key="candidate.username"
                        :label="candidate.displayName === candidate.username ? candidate.username : `${candidate.displayName}（${candidate.username}）`"
                        :value="candidate.username"
                    />
                </el-select>
            </el-form-item>
            <el-form-item label="密码" prop="password">
                <el-input ref="passwordInputRef" v-model="form.password" type="password" show-password placeholder="请输入密码" @keyup.enter="submit" />
            </el-form-item>
        </el-form>

        <template #footer>
            <el-button size="large" @click="visible = false">取消</el-button>
            <el-button type="primary" size="large" :loading="submitting" :disabled="candidateLoading || candidates.length === 0" @click="submit">确认切换</el-button>
        </template>
    </el-dialog>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import authApi from '@/api/system/auth.js'
import { useUserStore } from '@/store/index.js'

const props = defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue', 'success', 'closed'])
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const userStore = useUserStore()

const formRef = ref(null)
const passwordInputRef = ref(null)
const candidateLoading = ref(false)
const submitting = ref(false)
const candidates = ref([])
const form = ref({ username: '', password: '' })
const rules = {
    username: [{ required: true, message: '请选择收银员', trigger: 'change' }],
    password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const loadCandidates = async () => {
    candidateLoading.value = true
    try {
        const res = await authApi.getLoginCandidates('pos')
        candidates.value = res.data || []
        if (candidates.value.length === 0) ElMessage.warning('暂无可用的收银员账号')
    } catch (error) {
        candidates.value = []
        ElMessage.error('无法获取收银员列表，请确认后台服务正常运行')
    } finally {
        candidateLoading.value = false
        nextTick(() => passwordInputRef.value?.focus())
    }
}

const submit = async () => {
    const valid = await formRef.value?.validate()
    if (!valid) return

    submitting.value = true
    try {
        const user = await userStore.switchCashier(form.value)
        emit('success', user?.info || user)
        visible.value = false
    } catch (error) {
        console.error('切换收银员失败:', error)
    } finally {
        submitting.value = false
    }
}

const resetForm = () => {
    form.value = { username: '', password: '' }
    candidates.value = []
    formRef.value?.clearValidate()
}

const handleClosed = () => {
    resetForm()
    emit('closed')
}
</script>
