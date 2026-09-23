import { getToken } from '@/composables/token.js';
import MoneyConfig from '@/money.config.js';
import { useUserStore } from '@/store';
import axios from 'axios';
// 🌟 必须多引入一个 ElMessageBox，用来召唤锁屏弹窗
import { ElMessage, ElMessageBox } from 'element-plus';

// ==========================================
// 🌟 核心修复：智能识别运行环境，动态修正 API 基准地址
// ==========================================
let apiBaseUrl = import.meta.env.VITE_BASE_URL;
// 如果发现是 Electron 打包后的 file:// 协议，强行指向本地的 Java 后端端口
if (window.location.protocol === 'file:') {
    apiBaseUrl = 'http://127.0.0.1:9101/money-pos';
}

// 创建 axios 实例
const instance = axios.create({
    baseURL: apiBaseUrl, // 替换为智能推导后的地址
    timeout: 30000,
});

// 请求拦截器
instance.interceptors.request.use(
    async (config) => {
        // 添加鉴权头
        const token = getToken();
        if (token) {
            config.headers[MoneyConfig.tokenHeader] = `${MoneyConfig.tokenType} ${token}`;
        }

        // 处理租户信息
        const tenantCode = window.location.search.match(/(^|&|\?)tenant=([^&]*)(&|$)/i)?.[2];
        if (tenantCode && (window.tenant == null || window.tenant.tenantCode !== tenantCode)) {
            try {
                // 这里也使用推导后的 config.baseURL 确保万无一失
                const { data } = await axios.get(`${config.baseURL}/tenants/byCode?code=${tenantCode}`);
                if (data.data) {
                    window.tenant = data.data;
                } else {
                    throw new Error('租户不存在');
                }
            } catch (error) {
                console.error('获取租户信息失败', error);
                throw error;
            }
        }
        if (window.tenant) {
            config.headers[MoneyConfig.tenantHeader] = window.tenant.id;
        }

        // 添加请求 ID
        config.headers[MoneyConfig.requestIdHeader] = new Date().getTime();

        // 添加国际化头
        config.headers[MoneyConfig.i18nHeader] = MoneyConfig.lang;

        // 添加时区头
        config.headers[MoneyConfig.timezoneHeader] = MoneyConfig.timezone;

        return config;
    },
    (error) => {
        // 请求错误处理
        console.error('请求发送失败', error);
        return Promise.reject(error);
    }
);

// 响应拦截器
instance.interceptors.response.use(
    (response) => {
        const body = response.data;

        // 如果响应体没有 code 字段，直接返回
        if (!body.code) return body;

        // 处理非 200 状态码
        if (body.code !== 200) {
            const errorMsg = body.message || '服务器错误';
            const businessError = new Error(errorMsg);
            businessError.bizCode = body.code;
            businessError.bizData = body.data;

            const handledCodes = response.config?.handledBusinessCodes || [];
            if (!handledCodes.includes(body.code)) {
                ElMessage.error(errorMsg);
                businessError.isGlobalMessageShown = true;
            }
            return Promise.reject(businessError);
        }

        return body;
    },
    (error) => {
        // 网络错误处理
        if (error.message === 'Network Error') {
            ElMessage.error('网络错误，请检查网络连接');
            error.isGlobalMessageShown = true;
            return Promise.reject(error);
        }

        // HTTP 状态码处理
        const status = error.response?.status;
        switch (status) {
            case 401:
                // 🌟 落地 4：防丢单锁屏保护机制
                // 探测当前是否在收银台页面（防止丢失购物车数据）
                if (window.location.hash.includes('/pos')) {
                    // 防止多个并发请求同时失败导致弹出无数个框
                    if (!window.isLocking) {
                        window.isLocking = true;
                        const savedUsername = localStorage.getItem('vanapos_remember_username') || '';

                        ElMessageBox.prompt(
                            `系统检测到登录状态已过期。\n为防止购物车数据丢失，请输入账号【${savedUsername || '当前用户'}】的密码解锁：`,
                            'VanaPOS 安全锁屏保护',
                            {
                                confirmButtonText: '验证并解锁',
                                cancelButtonText: '放弃数据并退出',
                                inputType: 'password',
                                inputPattern: /.+/,
                                inputErrorMessage: '密码不能为空',
                                closeOnClickModal: false,
                                closeOnPressEscape: false,
                                showClose: false
                            }
                        ).then(async ({ value }) => {
                            try {
                                // 尝试静默重新登录
                                await useUserStore().login({ username: savedUsername, password: value });
                                ElMessage.success('身份验证成功，收银台已恢复！');
                                window.isLocking = false;
                                // 注意：虽然解锁成功，但刚才触发 401 的那次点击可能需要收银员再点一次
                            } catch (e) {
                                window.isLocking = false;
                                ElMessage.error('密码验证失败，已强制注销');
                                useUserStore().logout();
                            }
                        }).catch(() => {
                            // 点击了放弃按钮
                            window.isLocking = false;
                            useUserStore().logout();
                        });
                    }
                } else {
                    // 普通后台页面，直接踢出去
                    ElMessage.error('登录状态已过期，请重新登录');
                    useUserStore().logout();
                }
                error.isGlobalMessageShown = true;
                break;
            case 403:
                ElMessage.error('您没有权限执行此操作');
                error.isGlobalMessageShown = true;
                break;
            default:
                ElMessage.error(error.response?.data?.message || error.message || '请求失败');
                error.isGlobalMessageShown = true;
        }

        return Promise.reject(error);
    }
);

export default instance;
