import {defineStore} from 'pinia'
import authApi from '@/api/system/auth.js'
import userApi from '@/api/system/user.js'
import {getToken, removeToken, setToken} from "@/composables/token.js"

export const useUserStore = defineStore('user', {
    state: () => ({
        info: null,
        roles: null,
        permissions: null
    }),
    getters: {
        level: (state) => state.roles[0].level,
        permissionCode: (state) =>
            [...state.roles.map(e => e.roleCode), ...state.permissions.map(e => e.permission)].filter(e => e.length > 0)
    },
    actions: {
        /**
         * 是否有权限
         * @param codes
         * @returns {*|boolean}
         */
        hasPermission(codes) {
            if (this.level === 0) return true
            if (!codes) return false
            if (Array.isArray(codes)) {
                return this.permissionCode.some(e => codes.includes(e))
            }
            return this.permissionCode.includes(codes)
        },
        /**
         * 登录
         * @param data
         * @returns {Promise<unknown>}
         */
        login(data) {
            return new Promise((resolve, reject) => {
                authApi.login(data)
                    .then(res => {
                        const {data} = res
                        setToken(data.accessToken)
                        resolve()
                    })
                    .catch(err => reject(err))
            })
        },
        /**
         * 在 POS 内切换收银员。仅在新账号认证成功后才替换当前会话，避免密码输错导致当前收银员被登出。
         */
        async switchCashier(data) {
            const previousToken = getToken()
            const res = await authApi.login(data)
            const nextToken = res?.data?.accessToken
            if (!nextToken) throw new Error('切换收银员失败，未获取到有效登录凭证')

            if (previousToken) {
                try {
                    await authApi.logout()
                } catch (error) {
                    console.warn('原收银员会话注销失败，将在到期后失效', error)
                }
            }

            setToken(nextToken)
            this.info = null
            this.roles = null
            this.permissions = null
            return this.loadInfo()
        },
        /**
         * 加载用户信息
         * @returns {Promise<unknown>}
         */
        loadInfo() {
            return new Promise((resolve, reject) => {
                authApi.getInfo()
                    .then(res => {
                        const {data} = res
                        this.info = data.info
                        this.roles = data.roles
                        this.permissions = data.permissions
                        resolve(data)
                    })
                    .catch(err => reject(err))
            })
        },
        /**
         * 登出
         * @returns {Promise<void>}
         */
        async logout() {
            if (getToken()) await authApi.logout().then(() => removeToken())
            this.info = null
            this.roles = null
            this.permissions = null
            removeToken()
            window.location.reload()
        },
        /**
         * 更新信息
         * @param data
         * @returns {Promise<unknown>}
         */
        updateInfo(data) {
            return new Promise((resolve, reject) => {
                userApi.updateInfo(data)
                    .then(() => resolve())
                    .catch(err => reject(err))
            })
        },
        /**
         * 修改密码
         * @param data
         * @returns {Promise<unknown>}
         */
        changePassword(data) {
            return new Promise((resolve, reject) => {
                userApi.changePassword(data)
                    .then(() => this.logout())
                    .catch(err => reject(err))
            })
        }
    }
})
