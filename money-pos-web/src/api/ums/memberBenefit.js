import { req } from '../index.js'

export default {
  overview: (params) => req({ url: '/ums/member-benefit/overview', method: 'GET', params }),
  targetPlanOptions: (params) => req({ url: '/ums/member-benefit/target-plan-options', method: 'GET', params }),
  createTargetPlan: (data) => req({ url: '/pos/target/plans', method: 'POST', data }),
  targetLogs: (planId) => req({ url: '/ums/member-benefit/target-logs', method: 'GET', params: { planId } }),
  tradeHistory: (memberId) => req({ url: '/member-benefit/trade-history', method: 'GET', params: { memberId } }),
  quantityPurchase: (data) => req({ url: '/pos/deferred-quantity/settle', method: 'POST', data }),
  quantityPickup: (data) => req({ url: '/pos/deferred-quantity/pickup', method: 'POST', data }),
  amountPurchase: (data) => req({ url: '/pos/amount-package/purchase', method: 'POST', data }),
  amountPickupPreview: (data) => req({ url: '/pos/amount-package/pickup-preview', method: 'POST', data }),
  amountPickup: (data) => req({ url: '/pos/amount-package/pickup', method: 'POST', data }),
  amountPickupRefund: (data) => req({ url: '/pos/amount-package/pickup-refund', method: 'POST', data }),
  targetSettle: (data) => req({ url: '/pos/target/settle', method: 'POST', data }),
  targetSupplement: (data) => req({ url: '/pos/target/supplement', method: 'POST', data }),
  targetWaive: (data) => req({ url: '/pos/target/waive', method: 'POST', data }),
  targetConfirm: (data) => req({ url: '/pos/target/confirm', method: 'POST', data })
}
