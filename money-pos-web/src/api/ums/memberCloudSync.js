import { req } from '../index.js'

export default {
  status: () => req({ url: '/ums/member-cloud-sync/status', method: 'GET' })
}
