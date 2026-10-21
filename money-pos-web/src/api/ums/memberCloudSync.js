import { req } from '../index.js'

export default {
  status: () => req({ url: '/ums/member-cloud-sync/status', method: 'GET' }),
  config: () => req({ url: '/ums/member-cloud-sync/config', method: 'GET' }),
  saveConfig: (data) => req({ url: '/ums/member-cloud-sync/config', method: 'POST', data })
}
