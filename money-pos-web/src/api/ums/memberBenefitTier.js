import { req } from '../index.js'
export default {
  list: () => req({ url: '/ums/member-benefit/tiers', method: 'GET' }),
  save: (data) => req({ url: '/ums/member-benefit/tiers', method: 'POST', data }),
  remove: (id) => req({ url: `/ums/member-benefit/tiers/${id}`, method: 'DELETE' }),
  pricing: (brandId) => req({ url: '/gms/brand/config', method: 'GET', params: { brandId } })
}
