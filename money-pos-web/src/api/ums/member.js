import { req } from '../index.js'

export default {
  list: (query) => req({ url: '/ums/member', method: 'GET', params: query }),
  add: (data) => req({ url: '/ums/member', method: 'POST', data }),
  edit: (data) => req({ url: '/ums/member', method: 'PUT', data }),
  del: (ids) => req({ url: '/ums/member', method: 'DELETE', data: ids }),
  recharge: (data) => req({ url: '/ums/member/recharge', method: 'post', data }),
  // 🌟 新增：获取充值单详情
  getRechargeOrderDetail: (orderNo) => req({ url: `/ums/member/recharge/order/${orderNo}`, method: 'GET' }),
  // 🌟 新增：红冲撤销
  voidRecharge: (data) => req({ url: '/ums/member/recharge/void', method: 'POST', data }),
  // 🌟 新增：获取会员资产变更流水 (用于在画像里展示)
  getMemberLogs: (query) => req({ url: '/ums/member/logs', method: 'GET', params: query }),

  importMembers: (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return req({
      url: '/ums/member/import',
      method: 'POST',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  downloadTemplate: () => req({
    url: '/ums/member/template',
    method: 'GET',
    responseType: 'blob'
  }),
  posSearch: (keyword) => req({
    url: '/ums/member/pos-search',
    method: 'GET',
    params: { keyword }
  }),
  getCouponRules: () => req({ url: '/ums/member/coupon-rules', method: 'GET' })
}
