import { req } from '../index.js'

export default {
  status: () => req({ url: '/ums/member-cloud-sync/status', method: 'GET' }),
  config: () => req({ url: '/ums/member-cloud-sync/config', method: 'GET' }),
  revealSecrets: () => req({ url: '/ums/member-cloud-sync/config/reveal-secrets', method: 'POST' }),
  saveConfig: (data) => req({ url: '/ums/member-cloud-sync/config', method: 'POST', data }),
  repair: () => req({ url: '/ums/member-cloud-sync/repair', method: 'POST' }),
  dispatch: () => req({ url: '/ums/member-cloud-sync/dispatch', method: 'POST' }),
  testConnection: () => req({ url: '/ums/member-cloud-sync/test-connection', method: 'POST' })
}
