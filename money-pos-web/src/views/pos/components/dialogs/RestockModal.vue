<template>
    <el-dialog
        v-model="visible"
        title="极速采购入库 (Inbound)"
        width="900px"
        top="5vh"
        destroy-on-close
        @open="initModal"
        @opened="handleOpened"
        @closed="$emit('closed')"
    >
        <div class="flex flex-col gap-4 min-h-[500px]" v-loading="loading">

            <div class="bg-blue-50 border border-blue-100 p-4 rounded-lg flex gap-4 items-center shrink-0 shadow-sm">
                <el-icon class="text-3xl text-blue-500"><Search /></el-icon>
                <div class="flex-1 relative">
                    <el-autocomplete
                        :key="autocompleteKey"
                        ref="scannerInput"
                        v-model="scanKeyword"
                        :fetch-suggestions="querySearchAsync"
                        placeholder="请将条码对准扫码枪 / 或输入商品拼音首字母、名称"
                        clearable
                        class="w-full restock-scanner"
                        value-key="name"
                        :trigger-on-focus="false"
                        :hide-loading="true"
                        @select="handleSelect"
                        @keyup.enter="handleScan"
                    >
                        <template #prefix><el-icon class="text-xl text-blue-500"><Search /></el-icon></template>
                        <template #default="{ item }">
                            <div class="flex justify-between items-center w-full">
                                <div>
                                    <span class="font-bold text-gray-800">{{ item.name }}</span>
                                    <span class="text-gray-400 text-xs ml-2">({{ item.barcode }})</span>
                                </div>
                                <span class="text-gray-500 text-sm">现库存: <b class="text-blue-500">{{ item.stock || 0 }}</b></span>
                            </div>
                        </template>
                    </el-autocomplete>
                </div>
            </div>

            <div class="flex-1 border border-gray-200 rounded-lg overflow-hidden flex flex-col bg-white">
                <div class="bg-gray-100 p-2 text-sm font-bold text-gray-600 grid grid-cols-12 gap-2 text-center border-b border-gray-200">
                    <div class="col-span-1">序号</div>
                    <div class="col-span-4 text-left pl-2">商品信息</div>
                    <div class="col-span-2">入库进价(￥)</div>
                    <div class="col-span-2">入库数量</div>
                    <div class="col-span-2">小计(￥)</div>
                    <div class="col-span-1">操作</div>
                </div>

                <div class="flex-1 overflow-y-auto p-2 space-y-2 max-h-[380px]">
                    <template v-if="inboundList.length > 0">
                        <div v-for="(item, index) in inboundList" :key="index"
                             class="grid grid-cols-12 gap-2 items-center text-center bg-gray-50 p-2 rounded border border-gray-100 hover:bg-blue-50">
                            <div class="col-span-1 text-gray-400 font-bold">{{ index + 1 }}</div>
                            <div class="col-span-4 text-left pl-2 flex flex-col overflow-hidden">
                                <span class="font-bold text-gray-800 truncate">{{ item.name }}</span>
                                <span class="text-xs text-gray-400 font-mono">{{ item.barcode }}</span>
                            </div>
                            <div class="col-span-2">
                                <input type="number" v-model.number="item.price"
                                    class="w-full text-center border border-gray-300 rounded py-1 px-1 focus:outline-none focus:border-blue-500 font-bold text-gray-700 bg-white"
                                    @focus="$event.target.select()"
                                />
                            </div>
                            <div class="col-span-2">
                                <input type="number" v-model.number="item.qty"
                                    :ref="el => setQtyRef(el, index)"
                                    @keyup.enter="jumpToNextQty(index)"
                                    @focus="$event.target.select()"
                                    class="w-full text-center border-2 border-blue-200 rounded py-1 px-1 focus:outline-none focus:border-blue-500 font-black text-blue-600 bg-white"
                                />
                            </div>
                            <div class="col-span-2 font-bold text-red-500">
                                {{ ((item.price || 0) * (item.qty || 0)).toFixed(2) }}
                            </div>
                            <div class="col-span-1">
                                <el-button type="danger" icon="Delete" circle plain size="small" @click="removeItem(index)" />
                            </div>
                        </div>
                    </template>
                    <div v-else class="h-full flex flex-col items-center justify-center text-gray-300 py-10">
                        <el-icon class="text-6xl mb-2"><Box /></el-icon>
                        <p class="font-bold">请通过扫码枪开始录入商品</p>
                    </div>
                </div>
            </div>

            <div class="bg-gray-50 p-4 rounded-lg border border-gray-200 flex justify-between items-end shrink-0">
                <div class="flex-1 mr-8">
                    <span class="text-sm font-bold text-gray-600 mb-1 block">入库备注：</span>
                    <el-input v-model="remark" placeholder="录入本次采购的备注信息..." maxlength="50" show-word-limit />
                </div>
                <div class="flex flex-col items-end">
                    <div class="text-gray-500 font-bold text-sm mb-1">本次入库预计总成本:</div>
                    <div class="text-3xl font-black text-red-500 tracking-tighter">
                        ￥{{ totalCost.toFixed(2) }}
                    </div>
                </div>
            </div>

        </div>

        <template #footer>
            <div class="flex justify-end gap-3 pt-2">
                <el-button @click="visible = false" size="large">关 闭</el-button>
                <el-button type="primary" @click="submitInbound" size="large" class="font-bold px-10 shadow-md" :loading="submitting" :disabled="inboundList.length === 0">
                    确认入库
                </el-button>
            </div>
        </template>

        <QuickAddGoodsModal
            v-model="quickAddVisible"
            :initBarcode="missingBarcode"
            @success="handleQuickAddSuccess"
            @closed="resetScanner"
        />
    </el-dialog>
</template>

<script setup>
import { ref, computed, nextTick, watch } from 'vue' // 🌟 引入 watch
import { Search, Box, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { req } from '@/api/index.js'
import inventoryApi from '@/api/gms/inventory.js'
import QuickAddGoodsModal from './QuickAddGoodsModal.vue'

const props = defineProps({
    modelValue: Boolean,
    initialGoods: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'closed', 'success'])
const visible = computed({ get: () => props.modelValue, set: (val) => emit('update:modelValue', val) })

const loading = ref(false)
const submitting = ref(false)
const scanKeyword = ref('')
const scannerInput = ref(null)
const autocompleteKey = ref(0)

// 🌟 本地存储 Key
const DRAFT_KEY = 'vana_pos_inbound_draft'

const remark = ref('')
const inboundList = ref([])
const qtyInputRefs = ref([])
const hasDraft = ref(false) // 是否存在草稿标识

const quickAddVisible = ref(false)
const missingBarcode = ref('')

// ==========================================
// 🌟 核心防线：草稿箱黑匣子引擎 (深度监听)
// ==========================================
watch(inboundList, (newVal) => {
    if (newVal && newVal.length > 0) {
        localStorage.setItem(DRAFT_KEY, JSON.stringify(newVal));
        hasDraft.value = true;
    } else {
        localStorage.removeItem(DRAFT_KEY);
        hasDraft.value = false;
    }
}, { deep: true }); // 深度监听，改价格改数量也会触发保存

watch(remark, (newVal) => {
    if (newVal) {
        localStorage.setItem(DRAFT_KEY + '_remark', newVal);
    } else {
        localStorage.removeItem(DRAFT_KEY + '_remark');
    }
});

// 手动清空草稿
const clearDraft = () => {
    ElMessageBox.confirm('确定要放弃当前的入库列表吗？', '清空草稿', { type: 'warning' }).then(() => {
        inboundList.value = [];
        remark.value = '';
        qtyInputRefs.value = [];
        resetScanner();
    }).catch(() => {});
};

const setQtyRef = (el, index) => {
    if (el) qtyInputRefs.value[index] = el
}

const jumpToNextQty = (currentIndex) => {
    const nextIndex = currentIndex + 1;
    if (nextIndex < inboundList.value.length) {
        const nextInput = qtyInputRefs.value[nextIndex];
        if (nextInput) {
            nextInput.focus();
            setTimeout(() => { nextInput.select() }, 10);
        }
    } else {
        ElMessage.success('录入完毕');
    }
}

const totalCost = computed(() => inboundList.value.reduce((sum, item) => sum + ((item.price || 0) * (item.qty || 0)), 0))

// 🌟 弹窗初始化时，优先抢救草稿！
const initModal = () => {
    const draftData = localStorage.getItem(DRAFT_KEY);
    const draftRemark = localStorage.getItem(DRAFT_KEY + '_remark');

    if (draftData) {
        try {
            inboundList.value = JSON.parse(draftData);
            remark.value = draftRemark || '';
            hasDraft.value = true;
        } catch (e) {
            inboundList.value = [];
            remark.value = '';
            hasDraft.value = false;
        }
    } else {
        inboundList.value = [];
        remark.value = '';
        hasDraft.value = false;
    }
    qtyInputRefs.value = [];
    if (props.initialGoods) addInboundGoods(props.initialGoods);
    resetScanner();
}

const handleOpened = () => {
    scannerInput.value?.focus();
}

const resetScanner = async () => {
    scanKeyword.value = '';
    autocompleteKey.value++;
    await nextTick();
    scannerInput.value?.focus();
}

const querySearchAsync = async (queryString, cb) => {
    if (!queryString || queryString.trim() === '') { cb([]); return; }
    try {
        const res = await req({
            url: '/pos/goods',
            method: 'GET',
            params: { barcode: queryString }
        });
        cb(res.data || []);
    } catch (e) { cb([]); }
}

const addInboundGoods = (item) => {
    const existing = inboundList.value.find(i => i.id === item.id);
    if (existing) {
        existing.qty = (Number(existing.qty) || 0) + 1;
    } else {
        inboundList.value.push({
            id: item.id,
            name: item.name,
            barcode: item.barcode,
            price: item.purchasePrice || 0,
            qty: 1
        });
    }
}

const handleSelect = (item) => {
    addInboundGoods(item);
    resetScanner();
}

const handleScan = async () => {
    if (!scanKeyword.value) return;
    try {
        const res = await req({ url: '/pos/goods', method: 'GET', params: { barcode: scanKeyword.value } });
        const items = res.data || [];
        if (items.length === 1) {
            handleSelect(items[0]);
        } else if (items.length > 1) {
            ElMessage.warning('匹配到多个商品，请手动选择');
            scannerInput.value?.focus();
        } else {
            ElMessageBox.confirm(`条码 [${scanKeyword.value}] 未录入系统，是否立即极速建档？`, '未建档商品', {
                confirmButtonText: '立即建档',
                cancelButtonText: '重新扫码',
                type: 'warning'
            }).then(() => {
                missingBarcode.value = scanKeyword.value;
                quickAddVisible.value = true;
            }).catch(() => {
                resetScanner();
            });
        }
    } catch (e) { resetScanner(); }
}

const handleQuickAddSuccess = (newGoods) => {
    if (newGoods) {
        handleSelect(newGoods);
    }
    resetScanner();
}

const removeItem = (index) => {
    inboundList.value.splice(index, 1);
    qtyInputRefs.value.splice(index, 1);
}

const submitInbound = async () => {
    try {
        await ElMessageBox.confirm('确定提交入库单并更新物理库存吗？', '确认入库', { type: 'warning' });
        submitting.value = true;
        const payload = {
            type: 'INBOUND',
            remark: remark.value || '前台采购入库',
            details: inboundList.value.map(item => ({
                goodsId: item.id,
                qty: item.qty,
                price: item.price
            }))
        };
        await inventoryApi.createInbound(payload);

        const completedItems = inboundList.value.map(item => ({ ...item }));

        // 🌟 提交成功后，彻底撕毁草稿！
        localStorage.removeItem(DRAFT_KEY);
        localStorage.removeItem(DRAFT_KEY + '_remark');
        inboundList.value = [];

        ElMessage.success('入库成功');
        emit('success', completedItems);
        visible.value = false;
    } catch (e) {
    } finally {
        submitting.value = false;
    }
}
</script>

<style scoped>
.restock-scanner :deep(.el-input__wrapper) {
    height: 44px;
    font-weight: bold;
}
input[type=number]::-webkit-inner-spin-button,
input[type=number]::-webkit-outer-spin-button {
    -webkit-appearance: none;
}
</style>
