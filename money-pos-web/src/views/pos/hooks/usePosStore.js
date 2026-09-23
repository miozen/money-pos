import { ref, computed } from 'vue';
import { req } from "@/api/index.js";
import Big from 'big.js';

// ==========================================
// 🌟 全局单例状态 (跨组件共享，必须放在函数外！)
// ==========================================
const cartList = ref([]);
const currentMember = ref({});
const isWaiveCoupon = ref(false);
const manualDiscount = ref(0);
const selectedCouponRule = ref(null);
const usedCouponCount = ref(0);
const paymentList = ref([]);

const trialResult = ref(null);
const reqId = ref('');
const isTrialing = ref(false);

const globalBrandsKv = ref({});
const globalMemberTypes = ref([]);

// 🌟 全局唯一的选中焦点
const activeItemIndex = ref(-1);

let currentTrialVersion = 0;
let trialTimer = null;
let trialResolvers = [];

export function usePosStore() {

    const initGlobalDicts = (brandsKv, memberTypes) => {
        globalBrandsKv.value = brandsKv;
        globalMemberTypes.value = memberTypes;
    };

    const getCartItemPrices = (item, member) => {
        const brandId = item.brandId;
        const levelCode = member?.brandLevels ? member.brandLevels[brandId] : null;

        let unitOriginalPrice = new Big(item.salePrice || 0);
        let unitRealPrice = unitOriginalPrice;
        let unitCoupon = new Big(0);

        if (levelCode && item.levelPrices && item.levelPrices[levelCode] !== undefined) {
            unitRealPrice = new Big(item.levelPrices[levelCode]);

            if (item.levelCoupons && item.levelCoupons[levelCode] !== undefined) {
                unitCoupon = new Big(item.levelCoupons[levelCode]);
            } else if (item.memberCoupon !== undefined) {
                unitCoupon = new Big(item.memberCoupon);
            } else {
                const diff = unitOriginalPrice.minus(unitRealPrice);
                unitCoupon = diff.gt(0) ? diff : new Big(0);
            }
        }
        return { unitOriginalPrice, unitRealPrice, unitCoupon };
    };

    const runTrial = () => {
        return new Promise((resolve) => {
            trialResolvers.push(resolve);
            isTrialing.value = true;
            clearTimeout(trialTimer);

            trialTimer = setTimeout(async () => {
                if (cartList.value.length === 0) {
                    trialResult.value = null;
                    isTrialing.value = false;
                    flushTrialResolvers();
                    return;
                }

                const version = Date.now();
                currentTrialVersion = version;

                const payload = {
                    member: currentMember.value.id || null,
                    usedCouponRuleId: selectedCouponRule.value ? selectedCouponRule.value.ruleId : null,
                    usedCouponCount: usedCouponCount.value || 0,
                    waiveCoupon: isWaiveCoupon.value,
                    manualDiscountAmount: manualDiscount.value || 0,
                    items: cartList.value.map(item => ({
                        goodsId: item.id,
                        quantity: Number(item.qty) || 1
                    }))
                };

                try {
                    const res = await req({ url: '/pos/trial', method: 'POST', data: payload });
                    if (version === currentTrialVersion) {
                        let realData = res.data || res;
                        trialResult.value = realData;
                    }
                } catch (error) {
                    console.error("计价引擎同步失败:", error);
                } finally {
                    if (version === currentTrialVersion) {
                        isTrialing.value = false;
                        flushTrialResolvers();
                    }
                }
            }, 300);
        });
    };

    const flushTrialResolvers = () => {
        const resolvers = trialResolvers;
        trialResolvers = [];
        resolvers.forEach(r => r());
    };

    const enrichedCartList = computed(() => {
        return cartList.value.map(item => {
            const qty = new Big(item.qty || 1);
            const { unitOriginalPrice, unitRealPrice, unitCoupon } = getCartItemPrices(item, currentMember.value);

            let displaySubtotalRetail = unitOriginalPrice.times(qty);
            let displaySubtotalMember = unitRealPrice.times(qty);
            let displaySubtotalPrivilege = displaySubtotalRetail.minus(displaySubtotalMember);
            let displayCouponDeduct = unitCoupon.times(qty);

            if (trialResult.value && trialResult.value.items) {
                const trialItem = trialResult.value.items.find(i => String(i.goodsId) === String(item.id));
                if (trialItem) {
                    displaySubtotalRetail = new Big(trialItem.subTotalRetail ?? displaySubtotalRetail.toNumber());
                    displaySubtotalMember = new Big(trialItem.subTotalMember ?? displaySubtotalMember.toNumber());
                    displaySubtotalPrivilege = new Big(trialItem.subTotalPrivilege ?? displaySubtotalPrivilege.toNumber());
                }
            }

            if (trialResult.value && !isWaiveCoupon.value && trialResult.value.actualCouponDeduct === 0) {
                displayCouponDeduct = new Big(0);
            }

            return {
                ...item,
                qty: qty.toNumber(),
                displayOriginalPrice: unitOriginalPrice.toNumber(),
                displayRealPrice: unitRealPrice.toNumber(),
                displaySubtotalRetail: displaySubtotalRetail.toNumber(),
                displaySubtotalMember: displaySubtotalMember.toNumber(),
                displaySubtotalPrivilege: displaySubtotalPrivilege.toNumber(),
                displayPrice: unitRealPrice.toNumber(),
                displaySubtotal: displaySubtotalMember.toNumber(),
                displayCouponDeduct: displayCouponDeduct.toNumber(),
                isPending: isTrialing.value
            };
        });
    });

    const getTrialItemInfo = (goodsId) => {
        if (!trialResult.value || !trialResult.value.items) return null;
        return trialResult.value.items.find(i => String(i.goodsId) === String(goodsId));
    };

    const totalCount = computed(() => cartList.value.reduce((sum, item) => sum + (Number(item.qty) || 0), 0));

    const totalAmount = computed(() => {
        if (trialResult.value && trialResult.value.retailAmount !== undefined && !isTrialing.value) return trialResult.value.retailAmount;
        return enrichedCartList.value.reduce((sum, item) => sum.plus(item.displaySubtotalRetail), new Big(0)).toNumber();
    });

    const memberAmount = computed(() => {
        if (trialResult.value && trialResult.value.memberAmount !== undefined && !isTrialing.value) return trialResult.value.memberAmount;
        return enrichedCartList.value.reduce((sum, item) => sum.plus(item.displaySubtotalMember), new Big(0)).toNumber();
    });

    const theoreticalCouponUsed = computed(() => {
        const frontendCalculatedCoupon = enrichedCartList.value.reduce((sum, item) => sum.plus(item.displayCouponDeduct || 0), new Big(0)).toNumber();

        if (trialResult.value && !isTrialing.value) {
            if (!isWaiveCoupon.value && trialResult.value.actualCouponDeduct !== undefined) {
                return trialResult.value.actualCouponDeduct;
            }
        }
        return frontendCalculatedCoupon;
    });

    const actualCouponUsed = computed(() => trialResult.value ? (trialResult.value.actualCouponDeduct || 0) : 0);
    const waivedCouponAmount = computed(() => trialResult.value ? (trialResult.value.waivedCouponAmount || 0) : 0);

    const participatingAmount = computed(() => {
        if (trialResult.value && trialResult.value.participatingAmount !== undefined && !isTrialing.value) return trialResult.value.participatingAmount;
        return enrichedCartList.value.reduce((sum, item) => {
            return item.isDiscountParticipable === 1 ? sum.plus(item.displaySubtotalMember) : sum;
        }, new Big(0)).toNumber();
    });

    const finalPayAmount = computed(() => {
        if (trialResult.value && trialResult.value.finalPayAmount !== undefined && !isTrialing.value) {
            return trialResult.value.finalPayAmount;
        }
        const manualDeduct = new Big(manualDiscount.value || 0);
        const voucherDeduct = new Big(selectedCouponRule.value?.deduction || 0).times(usedCouponCount.value || 0);
        const final = new Big(memberAmount.value).minus(manualDeduct).minus(voucherDeduct);
        return final.gt(0) ? final.toNumber() : 0;
    });

    const paymentStats = computed(() => {
        const targetPay = new Big(finalPayAmount.value || 0);
        const payments = paymentList.value || [];
        const aggregate = new Big(payments.find(p => p.code && p.code.includes('AGGREGATE'))?.amount || 0);
        const tendered = payments.reduce((sum, p) => p.code && p.code.includes('AGGREGATE') ? sum : sum.plus(p.amount || 0), new Big(0));
        const totalInputs = tendered.plus(aggregate);
        const change = totalInputs.gt(targetPay) ? totalInputs.minus(targetPay) : new Big(0);

        // ==========================================
        // 🌟 核心安全修正：拨乱反正，用 应付 减去 实付！不再自欺欺人！
        // ==========================================
        const unpaid = targetPay.gt(totalInputs) ? targetPay.minus(totalInputs) : new Big(0);

        return {
            targetPay: targetPay.toNumber(),
            tendered: tendered.toNumber(),
            aggregate: aggregate.toNumber(),
            change: change.toNumber(),
            totalInputs: totalInputs.toNumber(),
            unpaid: unpaid.toNumber()
        };
    });

    const prepareCheckout = () => { reqId.value = `REQ${Date.now()}`; };

    const addToCart = (goods) => {
        const existIndex = cartList.value.findIndex(item => item.id === goods.id);
        if (existIndex !== -1) {
            cartList.value[existIndex].qty = (Number(cartList.value[existIndex].qty) || 1) + 1;
            activeItemIndex.value = existIndex;
        } else {
            cartList.value.push({ ...goods, qty: 1 });
            activeItemIndex.value = cartList.value.length - 1;
        }
        runTrial();
    };

    const moveActiveIndex = (step) => {
        if (cartList.value.length === 0) return;
        let newIdx = activeItemIndex.value + step;
        if (newIdx < 0) newIdx = 0;
        if (newIdx >= cartList.value.length) newIdx = cartList.value.length - 1;
        activeItemIndex.value = newIdx;
    };

    const quickAdjustActiveItem = (delta) => {
        if (cartList.value.length === 0 || activeItemIndex.value === -1) return;
        const item = cartList.value[activeItemIndex.value];
        if (!item) return;

        const newQty = (Number(item.qty) || 1) + delta;
        if (newQty >= 1) {
            item.qty = newQty;
            runTrial();
        } else {
            removeItem(activeItemIndex.value);
        }
    };

    const removeItem = (index) => {
        cartList.value.splice(index, 1);
        activeItemIndex.value = cartList.value.length > 0 ? cartList.value.length - 1 : -1;
        runTrial();
    };

    const bindMember = (memberObj) => {
        currentMember.value = memberObj;
        isWaiveCoupon.value = false;
        selectedCouponRule.value = null;
        usedCouponCount.value = 0;
        runTrial();
    };

    const clearMember = () => {
        currentMember.value = {};
        isWaiveCoupon.value = false;
        selectedCouponRule.value = null;
        usedCouponCount.value = 0;
        runTrial();
    };

    const clearAll = () => {
        cartList.value = [];
        clearMember();
        manualDiscount.value = 0;
        paymentList.value = [];
        trialResult.value = null;
        currentTrialVersion = 0;
        activeItemIndex.value = -1;
    };

    const restoreOrder = (cartArray, memberObj) => {
        cartList.value = cartArray;
        currentMember.value = memberObj;
        activeItemIndex.value = cartArray.length > 0 ? cartArray.length - 1 : -1;
        runTrial();
    };

    const submitOrder = async (orderData) => {
        return await req({ url: '/pos/settleAccounts', method: 'POST', data: orderData });
    };

    const scanAndAddToCart = async (barcode) => {
        try {
            const res = await req({ url: '/pos/goods', method: 'GET', params: { barcode: barcode } });
            const items = res.data || [];
            if (items.length === 1) {
                addToCart(items[0]);
                return { success: true, goods: items[0] };
            } else if (items.length > 1) {
                return { success: false, reason: 'multiple', items };
            } else {
                return { success: false, reason: 'not_found', barcode };
            }
        } catch (e) {
            return { success: false, reason: 'error', error: e };
        }
    };

    const refreshCartGoods = async () => {
        const refreshedItems = await Promise.all(cartList.value.map(async (item) => {
            if (!item.barcode) return item;
            try {
                const res = await req({ url: '/pos/goods', method: 'GET', params: { barcode: item.barcode } });
                const goods = (res.data || []).find(candidate => String(candidate.id) === String(item.id));
                return goods ? { ...item, ...goods, qty: item.qty } : item;
            } catch (error) {
                console.error('刷新购物车商品库存失败:', error);
                return item;
            }
        }));
        cartList.value = refreshedItems;
        return runTrial();
    };

    return {
        cartList, enrichedCartList, currentMember, isWaiveCoupon, manualDiscount, selectedCouponRule, usedCouponCount, paymentList,
        totalCount, totalAmount, memberAmount, actualCouponUsed, waivedCouponAmount, finalPayAmount, theoreticalCouponUsed, participatingAmount,
        paymentStats,
        reqId, trialResult, isTrialing, activeItemIndex,
        addToCart, removeItem, bindMember, clearMember, clearAll, restoreOrder, submitOrder, runTrial, prepareCheckout, getCartItemPrices,
        getTrialItemInfo, scanAndAddToCart, globalBrandsKv, globalMemberTypes, initGlobalDicts,
        quickAdjustActiveItem, moveActiveIndex, refreshCartGoods
    };
}
