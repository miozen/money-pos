<template>
    <PageWrapper>
        <div class="mb-4 flex justify-between items-center">
            <h2 class="text-xl font-black text-gray-800 flex items-center gap-2 tracking-widest">
                <svg-icon name="sys-manage" class="w-6 h-6 text-blue-600" />
                小票打印与硬件设置
            </h2>
            <el-button type="primary" size="large" @click="saveConfig" :loading="saving" class="font-bold tracking-widest">
                <el-icon class="mr-1"><Check /></el-icon> 保存配置
            </el-button>
        </div>

        <div class="grid grid-cols-1 lg:grid-cols-2 gap-8 mt-6">
            <el-card shadow="never" class="border rounded-xl">
                <template #header>
                    <span class="font-bold text-gray-700">⚙️ 基础参数设置</span>
                </template>
                <el-form :model="form" label-width="120px" class="pr-6" v-loading="loading">
                    <el-divider content-position="left">硬件自动化控制</el-divider>

                    <el-form-item label="自动打印小票">
                        <el-switch v-model="form.autoPrint" active-text="结账后自动打印" inactive-text="手动打印" />
                    </el-form-item>

                    <el-form-item label="自动弹开钱箱">
                        <el-switch v-model="form.openDrawer" active-text="现金收款后自动弹开" inactive-text="不自动弹开" />
                    </el-form-item>

                    <el-divider content-position="left" class="!mt-8">小票排版内容</el-divider>

                    <el-form-item label="店铺名称 (抬头)">
                        <el-input v-model="form.shopName" placeholder="例如：万象收银" maxlength="20" show-word-limit />
                    </el-form-item>

                    <el-form-item label="头部欢迎语">
                        <el-input v-model="form.headerMsg" placeholder="例如：欢迎光临，祝您购物愉快" maxlength="30" />
                    </el-form-item>

                    <el-form-item label="联系电话">
                        <el-input v-model="form.shopPhone" placeholder="例如：138-8888-8888" maxlength="20" />
                    </el-form-item>

                    <el-form-item label="门店地址">
                        <el-input v-model="form.shopAddress" placeholder="例如：深圳市南山区xxx街道" maxlength="50" />
                    </el-form-item>

                    <el-form-item label="底部留言">
                        <el-input v-model="form.footerMsg" type="textarea" :rows="3" placeholder="例如：凭此小票七日内免费退换，谢谢惠顾！" maxlength="100" show-word-limit />
                    </el-form-item>
                </el-form>

                <div class="mt-8 p-4 bg-blue-50 rounded-lg border border-blue-100 flex items-center justify-between">
                    <div>
                        <div class="text-sm font-bold text-blue-800 mb-1">🔌 硬件联调测试</div>
                        <div class="text-xs text-blue-600">输入真实订单号，测试打印排版是否对齐。</div>
                    </div>
                    <div class="flex gap-2">
                        <el-input v-model="testOrderNo" placeholder="输入测试订单号" class="w-40" size="default" />
                        <el-button type="primary" plain @click="handleTestPrint" size="default">发送测试</el-button>
                    </div>
                </div>
            </el-card>

            <div class="flex flex-col items-center bg-gray-100 rounded-xl p-8 border border-gray-200 shadow-inner overflow-y-auto max-h-[780px]">
                <div class="text-gray-400 font-bold mb-4 tracking-widest"><el-icon><View /></el-icon> 58mm 小票排版实时预览</div>

                <div class="receipt-paper">
                    <div class="text-center">
                        <div class="font-black text-2xl mb-1 tracking-widest">{{ form.shopName || '未设置店名' }}</div>
                        <div class="text-sm" v-if="form.headerMsg">{{ form.headerMsg }}</div>
                    </div>

                    <div class="my-3 text-xs">
                        <div>单号: ORD2026031910001</div>
                        <div>时间: 2026-03-19 12:00:00</div>
                        <div class="dashed-line"></div>

                        <div class="flex font-bold mb-1">
                            <span class="w-[18%]">原价</span>
                            <span class="w-[18%]">现价</span>
                            <span class="w-[15%]">数量</span>
                            <span class="w-[18%]">优惠</span>
                            <span class="w-[31%] text-right">小计</span>
                        </div>
                        <div class="mb-1">
                            <div class="font-bold">测试商品A超长名字换行演示</div>
                            <div class="flex">
                                <span class="w-[18%]">19.9</span>
                                <span class="w-[18%]">9.9</span>
                                <span class="w-[15%]">x2</span>
                                <span class="w-[18%]">20.0</span>
                                <span class="w-[31%] text-right">19.8</span>
                            </div>
                        </div>
                        <div class="mb-1">
                            <div class="font-bold">普通商品B</div>
                            <div class="flex">
                                <span class="w-[18%]">88.0</span>
                                <span class="w-[18%]">88.0</span>
                                <span class="w-[15%]">x1</span>
                                <span class="w-[18%]">0.0</span>
                                <span class="w-[31%] text-right">88.0</span>
                            </div>
                        </div>
                        <div class="dashed-line"></div>

                        <div class="flex justify-between">
                            <span>总价: 127.8</span>
                            <span>件数: 3</span>
                        </div>
                        <div class="flex justify-between mb-1">
                            <span>优惠: 20.0</span>
                            <span>抹零: 0.0</span>
                        </div>
                        <div class="font-black text-base mt-1 mb-1">应收: 107.8</div>
                        <div class="dashed-line"></div>

                        <div>会员支付: 50.0</div>
                        <div>微信支付: 57.8</div>
                        <div>会员扣券: 5.0</div>
                        <div>满减抵扣: 10.0 (1张)</div>
                        <div class="dashed-line"></div>

                        <div>会员姓名: 李总指挥</div>
                        <div>会员券余额: 288.00</div>
                        <div class="dashed-line"></div>

                        <div class="mt-2 text-left">
                            <div v-if="form.shopPhone">联系电话: {{ form.shopPhone }}</div>
                            <div v-if="form.shopAddress">门店地址: {{ form.shopAddress }}</div>
                        </div>
                    </div>

                    <div class="text-center text-xs mt-3">
                        <div class="whitespace-pre-wrap leading-relaxed" v-if="form.footerMsg">{{ form.footerMsg }}</div>
                    </div>
                </div>
            </div>
        </div>
    </PageWrapper>
</template>

<script setup>
import PageWrapper from "@/components/PageWrapper.vue";
import { Check, View, Wallet, Printer } from '@element-plus/icons-vue'
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import printApi from '@/api/system/print.js'

const loading = ref(false)
const saving = ref(false)
const testOrderNo = ref('')

const form = ref({
    shopName: '万象收银',
    shopPhone: '',
    shopAddress: '',
    headerMsg: '',
    footerMsg: '谢谢惠顾，欢迎下次光临！',
    autoPrint: true,
    openDrawer: true
})

onMounted(() => { loadConfig() })

const loadConfig = async () => {
    loading.value = true
    try {
        const res = await printApi.getConfig()
        if (res.data || res) {
            form.value = Object.assign(form.value, res.data || res)
        }
    } catch (e) {
        ElMessage.error("获取打印配置失败")
    } finally {
        loading.value = false
    }
}

const saveConfig = async () => {
    saving.value = true
    try {
        await printApi.updateConfig(form.value)
        ElMessage.success("配置保存成功！收银台将立即生效。")
    } catch (e) {
        ElMessage.error("保存失败：" + (e.message || '未知错误'))
    } finally {
        saving.value = false
    }
}

const handleTestPrint = async () => {
    if (!testOrderNo.value) { return ElMessage.warning("请先输入真实的订单号进行测试！") }
    try {
        await printApi.testHardwarePrint(testOrderNo.value)
        ElMessage.success("🖨️ 打印指令已发送！")
    } catch (e) {
        ElMessage.error("打印测试失败，请检查 USB 连接。")
    }
}
</script>

<style scoped>
.receipt-paper {
    background-color: #ffffff;
    width: 300px;
    min-height: 400px;
    padding: 24px 20px;
    box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
    color: #1a1a1a;
    font-family: 'Courier New', Courier, monospace;
    position: relative;
}
.receipt-paper::before {
    content: "";
    position: absolute;
    top: -6px;
    left: 0;
    width: 100%;
    height: 6px;
    background: linear-gradient(135deg, transparent 33%, #fff 33%, #fff 66%, transparent 66%),
                linear-gradient(45deg, transparent 33%, #fff 33%, #fff 66%, transparent 66%);
    background-size: 12px 12px;
}
.dashed-line {
    border-top: 1px dashed #333;
    margin: 8px 0;
    width: 100%;
}
</style>
