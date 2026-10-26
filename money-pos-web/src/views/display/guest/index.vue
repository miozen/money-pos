<template>
    <div class="guest-display-page">
        <div class="screen-inner">
            <div v-if="!settings.enabled" class="state-layer sleeping">
                <el-icon class="state-icon"><Monitor /></el-icon>
                <div class="state-title">DISPLAY SLEEPING</div>
                <div class="state-subtitle">客显系统已关闭</div>
            </div>

            <div v-else-if="posState === 'OFFLINE'" class="state-layer offline">
                <el-icon class="state-icon"><Link /></el-icon>
                <div class="state-title">系统连接中断</div>
                <div class="state-subtitle">正在尝试重新连接服务端...</div>
            </div>

            <template v-else-if="posState === 'STANDBY'">
                <div class="standby-stage">
                    <el-carousel
                        v-if="settings.playlist.length > 0"
                        height="100%"
                        :interval="settings.interval * 1000"
                        arrow="never"
                        indicator-position="none"
                        class="standby-carousel"
                    >
                        <el-carousel-item
                            v-for="(url, index) in settings.playlist"
                            :key="index"
                        >
                            <img :src="url" class="poster-image" />
                        </el-carousel-item>
                    </el-carousel>

                    <div v-else class="empty-standby">
                        <el-icon class="empty-icon"><PictureRounded /></el-icon>
                        <div class="empty-title">欢迎光临</div>
                        <div class="empty-subtitle">当前未配置待机海报</div>
                    </div>

                    <div class="marquee-bar">
                        <div class="marquee-text">
                            {{ settings.welcomeText || '欢迎光临！' }}
                        </div>
                    </div>
                </div>
            </template>

            <template v-else-if="posState === 'CASHIER'">
                <div class="cashier-stage">
                    <div class="left-panel">
                        <div class="left-header">
                            <span>购物清单 · 共 {{ totalItemCount }} 件</span>
                            <span v-if="realMember" class="member-tag">
                                {{ maskName(realMember.name || realMember.phone) }}
                            </span>
                        </div>

                        <div ref="cartContainerRef" class="cart-list">
                            <div
                                v-for="(item, idx) in realCart"
                                :key="idx"
                                class="cart-item"
                            >
                                <div class="item-main">
                                    <span class="item-name">{{ item.name || '商品' }}</span>
                                    <span class="item-qty">x {{ getQty(item) }}</span>
                                </div>

                                <div class="item-price-box">
                                    <span
                                        v-if="getOriginalPrice(item) > getPrice(item)"
                                        class="old-price"
                                    >
                                        ￥{{ getOriginalPrice(item).toFixed(2) }}
                                    </span>
                                    <span class="unit-price">
                                        ￥{{ getPrice(item).toFixed(2) }}
                                    </span>
                                </div>

                                <div class="item-subtotal">
                                    ￥{{ getSubtotal(item).toFixed(2) }}
                                </div>
                            </div>
                        </div>

                        <div
                            v-if="showParticipatingAmount"
                            class="participating-bar"
                        >
                            <el-icon><Present /></el-icon>
                            <span>
                                当前符合满减活动的总额：￥{{ realParticipatingAmount.toFixed(2) }}
                            </span>
                        </div>
                    </div>

                    <div class="right-panel">
                        <template v-if="realPayment.change > 0">
                            <div class="pay-label">需找零</div>
                            <div class="pay-main success-money">
                                ￥{{ realPayment.change.toFixed(2) }}
                            </div>
                            <div class="pay-tip">
                                应收：￥{{ realPayment.targetPay.toFixed(2) }}
                                &nbsp;&nbsp;
                                实收：￥{{ (realPayment.tendered + realPayment.aggregate).toFixed(2) }}
                            </div>
                        </template>

                        <template v-else-if="realPayment.aggregate > 0">
                            <div class="pay-label">
                                {{ realPayment.tendered > 0 ? '请扫码支付' : '请支付' }}
                            </div>
                            <div class="pay-main danger-money">
                                ￥{{ realPayment.aggregate.toFixed(2) }}
                            </div>

                            <div v-if="realPayment.tendered > 0" class="pay-tip warning-tip">
                                已收现金/余额：￥{{ realPayment.tendered.toFixed(2) }}
                            </div>

                            <div class="qr-group">
                                <div v-if="defaultPaymentCodes.length === 0" class="qr-empty">
                                    未配置收款码
                                </div>

                                <div
                                    v-else
                                    v-for="(code, cidx) in defaultPaymentCodes"
                                    :key="cidx"
                                    class="qr-item"
                                >
                                    <div class="qr-image-box">
                                        <img :src="code.url" class="qr-image" />
                                    </div>
                                    <span class="qr-name">{{ code.name }}</span>
                                </div>
                            </div>
                        </template>

                        <template
                            v-else-if="realPayment.tendered >= realPayment.targetPay && realPayment.targetPay > 0"
                        >
                            <div class="pay-label">已付清</div>
                            <div class="pay-main primary-money">
                                ￥{{ realPayment.targetPay.toFixed(2) }}
                            </div>
                        </template>

                        <template v-else>
                            <div class="pay-label">
                                {{ realPayment.tendered > 0 ? '还差金额' : '订单总计' }}
                            </div>
                            <div class="pay-main danger-money">
                                ￥{{ Math.max(realPayment.targetPay - realPayment.tendered, 0).toFixed(2) }}
                            </div>
                            <div v-if="realPayment.tendered > 0" class="pay-tip warning-tip">
                                已收：￥{{ realPayment.tendered.toFixed(2) }}
                            </div>
                        </template>
                    </div>
                </div>
            </template>

            <template v-else-if="posState === 'SUCCESS'">
                <div class="state-layer success">
                    <el-icon class="state-icon success-icon"><SuccessFilled /></el-icon>
                    <div class="success-title">支付成功</div>
                    <div class="state-subtitle">感谢惠顾，欢迎下次光临</div>
                </div>
            </template>
        </div>
    </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, computed, nextTick, watch } from 'vue'
import { Monitor, PictureRounded, SuccessFilled, Present, Link } from '@element-plus/icons-vue'
import axios from 'axios'
import { getToken } from '@/composables/token.js'
import { usePosSync } from '@/composables/usePosSync.js'

let apiBaseUrl = import.meta.env.VITE_BASE_URL
if (window.location.protocol === 'file:') {
    apiBaseUrl = 'http://127.0.0.1:9101/money-pos'
}

const headers = computed(() => {
    const token = getToken()
    return token ? { Authorization: 'Bearer ' + token } : {}
})

const settings = reactive({
    enabled: true,
    interval: 5,
    welcomeText: '欢迎光临！',
    paymentCodes: [],
    library: [],
    playlist: []
})

const {
    posState, realCart, realMember, realParticipatingAmount, realPayment,
    initReceiver, closeReceiver, getPrice, getOriginalPrice, getSubtotal, getQty
} = usePosSync()

const cartContainerRef = ref(null)

// 🌟 创建并监听跨标签页通讯频道
const displayChannel = new BroadcastChannel('pos_display_channel')

const defaultPaymentCodes = computed(() =>
    Array.isArray(settings.paymentCodes)
        ? settings.paymentCodes.filter(c => c && c.isDefault && c.url)
        : []
)

const showParticipatingAmount = computed(() => {
    return !!(
        realMember.value &&
        Array.isArray(realMember.value.couponList) &&
        realMember.value.couponList.length > 0 &&
        realParticipatingAmount.value > 0
    )
})

const totalItemCount = computed(() => realCart.value.reduce((total, item) => total + getQty(item), 0))

const maskName = (str) => {
    if (!str) return '会员'
    return str.length >= 2 ? str.substring(0, 1) + '**' : str
}

const fetchSettings = async () => {
    try {
        const res = await axios.get(`${apiBaseUrl}/common/display-settings`, {
            headers: headers.value
        })
        const rawData = res.data?.data ?? res.data
        if (rawData && rawData !== "{}") {
            const data = typeof rawData === 'string' ? JSON.parse(rawData) : rawData
            const internalBackendHost = '127.0.0.1:9101';

            const fixedPlaylist = (Array.isArray(data.playlist) ? data.playlist : [])
                .map(url => {
                    if (url && url.startsWith('http')) return url.replace('localhost:9101', internalBackendHost);
                    return url;
                }).filter(Boolean);

            const fixedPaymentCodes = (Array.isArray(data.paymentCodes) ? data.paymentCodes : [])
                .map(code => {
                    if (code && code.url && code.url.startsWith('http')) {
                        const fixedUrl = code.url.replace('localhost:9101', internalBackendHost);
                        return { ...code, url: fixedUrl };
                    }
                    return code;
                }).filter(Boolean);

            Object.assign(settings, {
                enabled: data.enabled ?? true,
                interval: data.interval ?? 5,
                welcomeText: data.welcomeText ?? '欢迎光临！',
                paymentCodes: fixedPaymentCodes,
                library: Array.isArray(data.library) ? data.library : [],
                playlist: fixedPlaylist
            })
        }
    } catch (e) {
        console.error('[GuestDisplay] 获取客显设置失败', e)
    }
}

watch(
    () => realCart.value,
    async () => {
        await nextTick()
        if (cartContainerRef.value) {
            cartContainerRef.value.scrollTop = cartContainerRef.value.scrollHeight
        }
    },
    { deep: true }
)

onMounted(async () => {
    await fetchSettings()
    initReceiver()

    // 🌟 监听后台发来的广播信号，收到信号后静默重新拉取设置！
    displayChannel.onmessage = (event) => {
        if (event.data === 'RELOAD_SETTINGS') {
            fetchSettings()
        }
    }
})

onUnmounted(() => {
    closeReceiver()
    displayChannel.close() // 销毁广播频道防内存泄漏
})
</script>

<style scoped>
.guest-display-page { width: 100vw; height: 100vh; overflow: hidden; background: #111827; }
.screen-inner { width: 100%; height: 100%; position: relative; background: #000; overflow: hidden; }
.state-layer { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; z-index: 20; }
.sleeping { background: #000; color: #374151; }
.offline { background: #111827; color: #d1d5db; }
.success { background: #fff; color: #111827; }
.state-icon { font-size: 72px; margin-bottom: 16px; opacity: 0.9; }
.success-icon { color: #22c55e; animation: bounce 1.2s infinite; }
.state-title, .success-title { font-size: 32px; font-weight: 900; letter-spacing: 2px; }
.state-subtitle { margin-top: 12px; font-size: 16px; color: #9ca3af; }
.success-title { color: #111827; }
.standby-stage { position: absolute; inset: 0; background: #000; }
.standby-carousel { width: 100%; height: 100%; }
.poster-image { width: 100%; height: 100%; object-fit: cover; }
.empty-standby { position: absolute; inset: 0; background: linear-gradient(135deg, #111827, #1f2937); display: flex; flex-direction: column; align-items: center; justify-content: center; color: #d1d5db; }
.empty-icon { font-size: 72px; margin-bottom: 18px; color: #6b7280; }
.empty-title { font-size: 36px; font-weight: 900; margin-bottom: 10px; }
.empty-subtitle { font-size: 18px; color: #9ca3af; }

/* 🌟 核心修复：跑马灯容器与文字绝对定位，配合动画引擎 */
.marquee-bar { position: absolute; left: 0; right: 0; bottom: 0; height: 64px; background: rgba(0, 0, 0, 0.7); backdrop-filter: blur(8px); display: flex; align-items: center; overflow: hidden; z-index: 10; }
.marquee-text { position: absolute; white-space: nowrap; color: #facc15; font-size: 28px; font-weight: 800; letter-spacing: 2px; animation: marquee 15s linear infinite; }

/* 🌟 核心修复：跑马灯专属关键帧动画 */
@keyframes marquee {
    0% { left: 100%; transform: translateX(0); }
    100% { left: 0%; transform: translateX(-100%); }
}

.cashier-stage { position: absolute; inset: 0; display: flex; background: #fff; }
.left-panel { width: 56%; height: 100%; display: flex; flex-direction: column; background: #f9fafb; border-right: 1px solid #e5e7eb; position: relative; }
.left-header { min-height: 64px; background: #2563eb; color: #fff; padding: 0 20px; display: flex; align-items: center; justify-content: space-between; font-size: 22px; font-weight: 800; flex-shrink: 0; }
.member-tag { background: #facc15; color: #1e3a8a; padding: 6px 12px; border-radius: 999px; font-size: 16px; }
.cart-list { flex: 1; overflow-y: auto; padding: 16px; padding-bottom: 72px; }
.cart-item { display: flex; align-items: center; justify-content: space-between; gap: 16px; background: #fff; border: 1px solid #f1f5f9; border-radius: 12px; padding: 14px 16px; margin-bottom: 12px; }
.item-main { display: flex; flex-direction: column; flex: 1; min-width: 0; }
.item-name { font-size: 20px; font-weight: 800; color: #1f2937; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.item-qty { margin-top: 6px; font-size: 16px; font-weight: 700; color: #9ca3af; }
.item-price-box { width: 120px; display: flex; flex-direction: column; align-items: flex-end; border-right: 1px solid #f1f5f9; padding-right: 14px; }
.old-price { color: #9ca3af; text-decoration: line-through; font-size: 14px; }
.unit-price { color: #6b7280; font-size: 16px; font-weight: 700; }
.item-subtotal { width: 150px; text-align: right; color: #111827; font-size: 24px; font-weight: 900; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; }
.participating-bar { position: absolute; left: 0; right: 0; bottom: 0; min-height: 56px; background: #eff6ff; border-top: 1px solid #bfdbfe; color: #2563eb; display: flex; align-items: center; justify-content: center; gap: 8px; font-size: 18px; font-weight: 800; padding: 0 12px; }
.right-panel { width: 44%; height: 100%; background: #fff; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 28px; text-align: center; }
.pay-label { color: #6b7280; font-size: 24px; font-weight: 800; margin-bottom: 12px; }
.pay-main { line-height: 1; margin-bottom: 20px; font-size: 72px; font-weight: 900; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; }
.success-money { color: #16a34a; }
.danger-money { color: #dc2626; }
.primary-money { color: #2563eb; }
.pay-tip { font-size: 18px; font-weight: 800; color: #6b7280; background: #f9fafb; border-radius: 12px; padding: 12px 16px; width: 100%; max-width: 520px; margin-top: 8px; }
.warning-tip { color: #ea580c; background: #fff7ed; }

/* ==========================================
   🌟 流式二维码矩阵
========================================== */
.qr-group {
    margin-top: 24px;
    width: 100%;
    display: flex;
    justify-content: center;
    gap: 20px;
    flex-wrap: wrap;
}

.qr-empty {
    color: #9ca3af; border: 2px dashed #e5e5; border-radius: 12px; padding: 24px 36px; font-size: 18px; font-weight: 700;
}

.qr-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    width: calc(50% - 10px);
    max-width: 200px;
}

.qr-image-box {
    padding: 10px;
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 16px;
    box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
    width: 100%;
    aspect-ratio: 1 / 1;
    display: flex;
    align-items: center;
    justify-content: center;
}

.qr-image {
    width: 100%;
    height: 100%;
    object-fit: cover;
}

.qr-name {
    margin-top: 12px;
    font-size: 18px;
    font-weight: 800;
    color: #4b5563;
    text-align: center;
}
</style>
