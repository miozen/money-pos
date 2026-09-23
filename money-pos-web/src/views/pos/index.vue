<template>
    <div class="pos-container absolute inset-0 z-40 flex flex-col overflow-hidden font-sans select-none bg-[#f3f4f6]">
        <HeaderBar :cashierName="cashierName" :currentTime="currentTime" @action="handleNavAction" />

        <div class="flex-1 overflow-hidden p-2">
            <CartTable class="h-full rounded-md shadow-sm overflow-hidden border border-gray-200" />
        </div>

        <BottomConsole
            ref="bottomConsoleRef"
            :lastOrder="lastOrder"
            :currentOrderNo="currentOrderNo"
            :currentTime="currentTime"
            :memberTypesDict="memberTypesDict"
            :suspendCount="suspendedOrderList.length"
            @open-checkout="dialogs.checkout = true"
            @open-drawer="openDrawer"
            @clear-cart="handleClearConfirm"
            @suspend="handleSuspendRetrieve"
            @quick-add="handleQuickAddTrigger"
        />

        <CheckoutModal v-model="dialogs.checkout" :pay-method-dict="payMethodDict" :pay-tag-dict="payTagDict" @checkout-success="handleCheckoutSuccess" @closed="keepFocus" />
        <RestockModal v-model="dialogs.restock" @closed="keepFocus" />
        <MemberBindModal v-model="dialogs.memberBind" @select="handleMemberSelect" @closed="keepFocus" />
        <RechargeModal v-model="dialogs.recharge" @closed="keepFocus" />
        <MemberAddModal v-model="dialogs.memberAdd" :memberTypesDict="memberTypesDict" @closed="keepFocus" />
        <SuspendModal v-model="dialogs.suspendList" :suspendedList="suspendedOrderList" @retrieve="retrieveOrder" @closed="keepFocus" />
        <SalesOrderModal v-model="dialogs.sales" @closed="keepFocus" />
        <ShiftModal v-model="dialogs.shift" :cashier-name="cashierName" @closed="keepFocus" />

        <QuickAddGoodsModal
            v-model="dialogs.quickAdd"
            :initBarcode="missingBarcode"
            @success="handleQuickAddSuccess"
            @closed="keepFocus"
        />
    </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch, toRaw } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { useUserStore } from "@/store/index.js"
import dictApi from "@/api/system/dict.js"
import brandApi from '@/api/gms/brand.js'

import { usePosStore } from './hooks/usePosStore'
import { useScanner } from './hooks/useScanner'
import { useDisplaySync } from './hooks/useDisplaySync'

import HeaderBar from './components/HeaderBar.vue'
import CartTable from './components/CartTable.vue'
import BottomConsole from './components/BottomConsole.vue'
import CheckoutModal from './components/CheckoutModal.vue'

import RestockModal from './components/dialogs/RestockModal.vue'
import MemberBindModal from './components/dialogs/MemberBindModal.vue'
import RechargeModal from './components/dialogs/RechargeModal.vue'
import MemberAddModal from './components/dialogs/MemberAddModal.vue'
import SuspendModal from './components/dialogs/SuspendModal.vue'
import SalesOrderModal from './components/dialogs/SalesOrderModal.vue'
import ShiftModal from './components/dialogs/ShiftModal.vue'
import QuickAddGoodsModal from './components/dialogs/QuickAddGoodsModal.vue'

const router = useRouter()
const userStore = useUserStore()

const { cartList, currentMember, totalAmount, clearAll, restoreOrder, scanAndAddToCart, addToCart, initGlobalDicts, quickAdjustActiveItem, moveActiveIndex } = usePosStore();

const bottomConsoleRef = ref(null)
const keepFocus = () => bottomConsoleRef.value?.focusInput()

const dialogs = reactive({
    checkout: false,
    restock: false,
    memberBind: false,
    recharge: false,
    memberAdd: false,
    suspendList: false,
    sales: false,
    shift: false,
    quickAdd: false
});

const isAnyDialogOpen = computed(() => Object.values(dialogs).some(isOpen => isOpen === true));
const { notifyPaySuccess, notifyIdle, notifyMemberBind } = useDisplaySync(computed(() => dialogs.checkout));

// ==========================================
// 🌟 终极事件捕获网 (完美解决 Esc 和 弹窗冲突)
// ==========================================
const handleGlobalKeyDown = (e) => {
    // 1. 🌟 Esc 键的最高优先级绿灯通道！
    if (e.key === 'Escape') {
        // 如果有 Element Plus 的下拉浮层（比如自动联想提示）开着，就不拦截，让系统自己收起下拉。
        const hasOpenPopper = document.querySelector('.el-popper:not([style*="display: none"])');
        if (hasOpenPopper) return;

        let closedAny = false;

        // 强制关闭主页面的所有弹窗
        for (const key in dialogs) {
            if (dialogs[key]) {
                dialogs[key] = false;
                closedAny = true;
            }
        }

        // 强制关闭底部控制台私有的弹窗
        if (bottomConsoleRef.value && bottomConsoleRef.value.closeAllDialogs) {
            if (bottomConsoleRef.value.closeAllDialogs()) {
                closedAny = true;
            }
        }

        // 如果真的关掉了某个弹窗，阻断事件并把焦点还给搜索框
        if (closedAny) {
            e.preventDefault();
            e.stopPropagation();
            keepFocus();
        }
        // Esc 逻辑结束，绝对不再触发清空购物车！
        return;
    }

    // 2. 如果当前有任何弹窗打开着，主页面立刻“闭嘴”，绝不拦截内部按键
    const isOverlayOpen = isAnyDialogOpen.value || (bottomConsoleRef.value && bottomConsoleRef.value.isDialogOpen);
    if (isOverlayOpen) return;

    // 以下为盲操快捷键拦截
    const target = e.target;
    const isInput = target.tagName === 'INPUT' || target.tagName === 'TEXTAREA';
    const isInputEmpty = isInput ? (!target.value || target.value.trim() === '') : true;

    // 上下键 (切焦点)
    if (e.key === 'ArrowUp' || e.key === 'ArrowDown') {
        if (isInputEmpty) {
            e.preventDefault();
            e.stopPropagation();
            const step = e.key === 'ArrowUp' ? -1 : 1;
            moveActiveIndex(step);
            return;
        }
    }

    // 加减键
    if (e.key === '+' || e.key === '=' || e.key === '-' || e.key === '_') {
        if (isInputEmpty) {
            e.preventDefault();
            e.stopPropagation();
            const delta = (e.key === '+' || e.key === '=') ? 1 : -1;
            quickAdjustActiveItem(delta);
            return;
        }
    }

    // 空格收款
    if (e.key === ' ' || e.code === 'Space') {
        if (isInputEmpty) {
            e.preventDefault();
            e.stopPropagation();
            if (cartList.value.length > 0) dialogs.checkout = true;
            else ElMessage.warning('购物车是空的，请先扫码商品');
            return;
        }
    }
}

const missingBarcode = ref('')

const handleQuickAddTrigger = (barcode) => {
    missingBarcode.value = barcode;
    dialogs.quickAdd = true;
}

const handleQuickAddSuccess = (newGoods) => {
    if (newGoods) {
        addToCart(newGoods);
        ElMessage.success(`[${newGoods.name}] 建档成功并已加入收银车！`);
    }
    keepFocus();
}

useScanner({
    onEnter: async (buffer) => {
        if (!isAnyDialogOpen.value && !(bottomConsoleRef.value && bottomConsoleRef.value.isDialogOpen)) {
            if (buffer && buffer.length > 0) {
                const res = await scanAndAddToCart(buffer);
                if (!res.success && res.reason === 'not_found') {
                    ElMessageBox.confirm(`条码 [${res.barcode}] 未录入系统，是否立即极速建档？`, '未建档商品', {
                        confirmButtonText: '立即建档',
                        cancelButtonText: '取消',
                        type: 'warning'
                    }).then(() => {
                        handleQuickAddTrigger(res.barcode);
                    }).catch(() => {
                        keepFocus();
                    });
                } else if (!res.success && res.reason === 'multiple') {
                    ElMessage.warning('匹配到多个商品，请手动到明细中搜索');
                }
                return;
            }
            if (cartList.value.length > 0) { dialogs.checkout = true; }
        }
    }
})

const generateOrderNo = () => {
    const timePart = dayjs().format('YYYYMMDDHHmmss');
    const randomPart = Math.floor(Math.random() * 10000).toString().padStart(4, '0');
    return `POS${timePart}${randomPart}`;
}

const currentOrderNo = ref(generateOrderNo())
const currentTime = ref(dayjs().format('YYYY-MM-DD HH:mm:ss'))
const lastOrder = ref({ total: 0, paid: 0, couponUsed: 0 })
const suspendedOrderList = ref([])

const payMethodDict = ref([])
const payTagDict = ref([])
const memberTypesDict = ref([])

const cashierName = computed(() => {
    const info = userStore.info;
    if (info) return info.nickname || info.nickName || info.name || info.realName || info.username || info.userName || '管理员';
    try {
        const localUserStr = localStorage.getItem('user') || localStorage.getItem('userInfo');
        if (localUserStr) {
            const parsed = JSON.parse(localUserStr);
            const localInfo = parsed.info || parsed;
            const name = localInfo.nickname || localInfo.nickName || localInfo.name || localInfo.username;
            if (name) return name;
        }
    } catch (e) {}
    return '未知收银员';
});

let clockTimer = null;

onMounted(async () => {
    let fetchedMemberTypes = [];
    let fetchedBrandsKv = {};

    try {
        const dict = await dictApi.loadDict(["memberType", "pos_payment_method", "paySubTag"])
        if (dict.memberType) {
            memberTypesDict.value = dict.memberType;
            fetchedMemberTypes = dict.memberType;
        }
        if (dict.pos_payment_method) payMethodDict.value = dict.pos_payment_method;
        if (dict.paySubTag) payTagDict.value = dict.paySubTag;
    } catch (e) { }

    try {
        const brandRes = await (brandApi.list ? brandApi.list({ size: 1000 }) : brandApi.getSelect())
        const brandList = brandRes?.data?.records || brandRes?.data || brandRes?.records || brandRes || []
        brandList.forEach(e => { fetchedBrandsKv[e.id || e.value] = e.name || e.label })
    } catch (e) {}

    initGlobalDicts(fetchedBrandsKv, fetchedMemberTypes);

    try {
        const localSuspend = localStorage.getItem('pos_suspended_orders');
        if (localSuspend) suspendedOrderList.value = JSON.parse(localSuspend);
    } catch (e) {}

    keepFocus();
    clockTimer = setInterval(() => currentTime.value = dayjs().format('YYYY-MM-DD HH:mm:ss'), 1000)

    window.addEventListener('keydown', handleGlobalKeyDown, true);
})

onUnmounted(() => {
    if (clockTimer) clearInterval(clockTimer);
    window.removeEventListener('keydown', handleGlobalKeyDown, true);
})

watch(suspendedOrderList, (newVal) => { localStorage.setItem('pos_suspended_orders', JSON.stringify(newVal)); }, { deep: true });

const handleNavAction = (action) => {
    if (action === 'shift') dialogs.shift = true
    else if (action === 'sales') dialogs.sales = true
    else if (action === 'admin') {
        if (window.moneyPosDesktop?.openAdminWindow) {
            window.moneyPosDesktop.openAdminWindow()
        } else {
            window.open(router.resolve({ path: '/login', query: { entry: 'admin' } }).href, '_blank', 'noopener,noreferrer')
        }
    }
    else if (action === 'restock') dialogs.restock = true
    else if (action === 'recharge') dialogs.recharge = true
    else if (action === 'addMember') dialogs.memberAdd = true
}

const handleClearConfirm = () => {
    if(cartList.value.length === 0) return;
    ElMessageBox.confirm('⚠️ 购物车非空，确定要彻底清空当前所有商品吗？', '高危操作警告', {
        confirmButtonText: '确定清空',
        cancelButtonText: '点错了',
        type: 'error',
    }).then(() => {
        handleClear();
        ElMessage.success('已清空订单');
    }).catch(() => {
        keepFocus();
    });
}

const handleClear = () => {
    clearAll();
    currentOrderNo.value = generateOrderNo();
    keepFocus();
    notifyIdle();
}

const openDrawer = () => ElMessage.success('指令：弹开钱箱')

const handleCheckoutSuccess = (orderSummary) => {
    lastOrder.value = orderSummary;
    openDrawer();
    handleClear();
    notifyPaySuccess(orderSummary.total);
}

const handleMemberSelect = (member) => {
    currentMember.value = member;
    notifyMemberBind(member);
}

const handleSuspendRetrieve = () => {
    if (cartList.value.length > 0) {
        const snapshotCart = cartList.value.map(item => structuredClone(toRaw(item)));
        const snapshotMember = structuredClone(toRaw(currentMember.value));

        suspendedOrderList.value.push({
            id: Date.now(),
            time: dayjs().format('HH:mm:ss'),
            cart: snapshotCart,
            member: snapshotMember,
            total: totalAmount.value
        });

        handleClear();
        ElMessage.success('🛡️ 订单已挂起并安全落盘！');
    }
    else if (suspendedOrderList.value.length > 0) { dialogs.suspendList = true; }
    else { ElMessage.warning('当前没有挂单记录'); }
}

const retrieveOrder = (index) => {
    const order = suspendedOrderList.value[index];
    restoreOrder(order.cart, order.member);
    suspendedOrderList.value.splice(index, 1);
    ElMessage.success('订单已取回');
    notifyMemberBind(order.member);
}
</script>
