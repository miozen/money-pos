<template>
  <el-dialog v-model="visible" title="合并重复会员档案" width="680px" destroy-on-close @closed="reset">
    <el-alert type="warning" :closable="false" show-icon class="mb-4">
      <template #title>请确认方向：来源会员会停用，保留会员继续使用；此操作不可自动撤销。</template>
    </el-alert>

    <template v-if="source">
      <div class="grid grid-cols-1 md:grid-cols-2 gap-3 mb-4">
        <section class="rounded border border-orange-200 bg-orange-50 p-3">
          <div class="text-xs text-orange-700 mb-1">来源会员（合并后停用）</div>
          <div class="font-bold">{{ source.name }} <span class="text-gray-500 font-normal">{{ source.phone }}</span></div>
        </section>
        <section class="rounded border border-blue-200 bg-blue-50 p-3">
          <div class="text-xs text-blue-700 mb-1">保留会员（继续使用）</div>
          <div v-if="target" class="font-bold">{{ target.name }} <span class="text-gray-500 font-normal">{{ target.phone }}</span></div>
          <div v-else class="text-gray-400">请在下方手工选择</div>
        </section>
      </div>

      <el-form label-width="108px">
        <el-form-item label="保留会员" required>
          <MemberSmartSearch v-model="targetId" size="default" placeholder="搜索并选择保留会员" @select="selectTarget" @clear="clearTarget" />
        </el-form-item>
        <el-form-item label="合并原因" required>
          <el-input v-model.trim="reason" maxlength="255" show-word-limit placeholder="例如：早期导入造成重复档案" />
        </el-form-item>
      </el-form>

      <div v-if="preview" class="rounded border p-3" :class="preview.mergeAllowed ? 'border-green-200 bg-green-50' : 'border-red-200 bg-red-50'">
        <div class="font-bold mb-2" :class="preview.mergeAllowed ? 'text-green-700' : 'text-red-700'">
          {{ preview.mergeAllowed ? '可以合并' : '当前不能合并' }}
        </div>
        <div class="grid grid-cols-2 gap-y-2 text-sm">
          <span>余额转移</span><b>￥{{ money(preview.source?.balance) }}</b>
          <span>未使用满减券转移</span><b>{{ preview.sourceUnusedCouponCount || 0 }} 张</b>
          <span>历史订单</span><span>{{ preview.sourceOrderCount || 0 }} 笔（保留原归属）</span>
          <span>金额型券额度</span><span>￥{{ money(preview.source?.coupon) }}（本版不转移）</span>
        </div>
        <ul v-if="preview.blockers?.length" class="mt-3 text-red-700 text-sm list-disc pl-5">
          <li v-for="blocker in preview.blockers" :key="blocker">{{ blocker }}</li>
        </ul>
      </div>

      <el-alert v-if="merged" type="success" :closable="false" show-icon class="mt-4">
        <template #title>来源会员“{{ source.name }}”已合并至“{{ target?.name }}”。</template>
      </el-alert>
    </template>

    <template #footer>
      <el-button @click="visible = false">{{ merged ? '关闭' : '取消' }}</el-button>
      <el-button v-if="!merged" type="danger" :disabled="!canSubmit" :loading="submitting" @click="submit">确认合并</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import MemberSmartSearch from '@/components/common/MemberSmartSearch.vue'
import memberApi from '@/api/ums/member.js'

const props = defineProps({ modelValue: Boolean, source: Object })
const emit = defineEmits(['update:modelValue', 'success'])
const targetId = ref(null), target = ref(null), preview = ref(null), reason = ref(''), submitting = ref(false), merged = ref(false)
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const canSubmit = computed(() => Boolean(target.value && reason.value && preview.value?.mergeAllowed))
const money = value => Number(value || 0).toFixed(2)

watch(() => props.source, () => reset())

async function selectTarget(member) {
  if (!props.source || String(member.id) === String(props.source.id)) {
    ElMessage.warning('来源会员和保留会员不能相同')
    clearTarget()
    return
  }
  target.value = member
  targetId.value = member.id
  await loadPreview()
}
function clearTarget() { targetId.value = null; target.value = null; preview.value = null }
async function loadPreview() {
  if (!props.source || !target.value) return
  try {
    const res = await memberApi.mergePreview({ sourceMemberId: props.source.id, targetMemberId: target.value.id })
    preview.value = res.data || res
  } catch (e) { preview.value = null }
}
async function submit() {
  await ElMessageBox.confirm(
    `确认将“${props.source.name}”合并至“${target.value.name}”？来源会员将停止使用，余额和未使用满减券会按预览转移。`,
    '二次确认', { confirmButtonText: '确认合并', cancelButtonText: '取消', type: 'warning' }
  )
  submitting.value = true
  try {
    await memberApi.merge({ requestNo: `MERGE-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`, sourceMemberId: props.source.id, targetMemberId: target.value.id, reason: reason.value })
    merged.value = true
    ElMessage.success('会员档案已合并')
    emit('success', { source: props.source, target: target.value })
  } finally { submitting.value = false }
}
function reset() { targetId.value = null; target.value = null; preview.value = null; reason.value = ''; submitting.value = false; merged.value = false }
</script>
