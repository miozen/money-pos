package com.money.service.printer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.member.MemberCouponCountQuery;
import com.money.dto.Finance.FinanceDataVO;
import com.money.dto.OmsOrder.OrderDetailVO;
import com.money.dto.OmsOrderDetail.OmsOrderDetailVO;
import com.money.entity.SysDictDetail;
import com.money.feature.sys.infrastructure.persistence.entity.SysPrintConfig;
import com.money.mapper.SysDictDetailMapper;
import com.money.mapper.SysPrintConfigMapper;
import com.money.util.EscPosUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.print.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class PosPrinterService {

    private final SysPrintConfigMapper sysPrintConfigMapper;
    private final SysDictDetailMapper sysDictDetailMapper;
    private final MemberCouponCountQuery memberCouponCountQuery;

    /**
     * 打印订单小票；是否自动开钱箱由调用方传入的结算语义决定。
     * 重打小票不可传 true，避免重打时意外弹开钱箱。
     */
    public void printReceipt(OrderDetailVO orderVO, boolean openDrawerForCashPayment) {
        try {
            SysPrintConfig config = sysPrintConfigMapper.selectById(1L);
            if (config == null) return;

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(EscPosUtil.INIT);

            if (openDrawerForCashPayment && Boolean.TRUE.equals(config.getOpenDrawer())) {
                bos.write(EscPosUtil.OPEN_CASH_DRAWER);
            }
            if (config.getAutoPrint() == null || !config.getAutoPrint()) {
                executeHardwareCommand(bos.toByteArray());
                return;
            }

            boolean isVip = orderVO.getVip() != null && orderVO.getVip();

            // 1. 头部区域
            bos.write(EscPosUtil.ALIGN_CENTER);
            if (StringUtils.hasText(config.getShopName())) {
                bos.write(EscPosUtil.BOLD_ON);
                bos.write(EscPosUtil.TEXT_LARGE);
                writeText(bos, config.getShopName() + "\n\n");
                bos.write(EscPosUtil.TEXT_NORMAL);
                bos.write(EscPosUtil.BOLD_OFF);
            }
            if (StringUtils.hasText(config.getHeaderMsg())) {
                writeText(bos, config.getHeaderMsg() + "\n");
            }
            writeText(bos, "\n");

            // 2. 基础信息
            bos.write(EscPosUtil.ALIGN_LEFT);
            writeText(bos, "单号: " + orderVO.getOrderNo() + "\n");
            writeText(bos, "时间: " + orderVO.getCreateTime() + "\n");
            writeText(bos, "--------------------------------\n");

            // 3. 商品明细
            writeText(bos, formatItemLine("原价", "现价", "数量", "优惠", "小计") + "\n");

            int totalQty = 0;
            for (OmsOrderDetailVO item : orderVO.getOrderDetails()) {
                totalQty += item.getQuantity();
                writeText(bos, item.getGoodsName() + "\n");

                String orig = fmt(item.getSalePrice());
                String curr = fmt(item.getGoodsPrice());
                String qty = "x" + item.getQuantity();
                BigDecimal discount = item.getSalePrice().subtract(item.getGoodsPrice()).multiply(new BigDecimal(item.getQuantity()));
                String disc = discount.compareTo(BigDecimal.ZERO) > 0 ? fmt(discount) : "0.00";
                String subtotal = fmt(item.getGoodsPrice().multiply(new BigDecimal(item.getQuantity())));

                writeText(bos, formatItemLine(orig, curr, qty, disc, subtotal) + "\n");
            }
            writeText(bos, "--------------------------------\n");

            // 4. 汇总信息
            BigDecimal totalDiscount = orderVO.getTotalAmount().subtract(orderVO.getPayAmount());
            BigDecimal waived = orderVO.getWaivedCouponAmount() != null ? orderVO.getWaivedCouponAmount() : BigDecimal.ZERO;

            writeText(bos, "总价: " + padRight(fmt(orderVO.getTotalAmount()), 11) + "件数: " + totalQty + "\n");
            writeText(bos, "优惠: " + padRight(fmt(totalDiscount), 11) + "抹零: " + fmt(waived) + "\n");

            bos.write(EscPosUtil.BOLD_ON);
            writeText(bos, "应收: " + fmt(orderVO.getPayAmount()) + "\n");
            bos.write(EscPosUtil.BOLD_OFF);
            writeText(bos, "--------------------------------\n");

            // 5. 支付与会员相关信息
            if (orderVO.getPayments() != null) {
                for (OrderDetailVO.OrderPayVO pay : orderVO.getPayments()) {
                    writeText(bos, resolvePayName(pay) + ": " + fmt(pay.getPayAmount()) + "\n");
                }
            }

            if (orderVO.getChangeAmount() != null && orderVO.getChangeAmount().compareTo(BigDecimal.ZERO) > 0) {
                writeText(bos, "找零: " + fmt(orderVO.getChangeAmount()) + "\n");
            }

            if (isVip) {
                // ==========================================
                // 🌟 您指出的致命盲区：已修复！
                // 将旧的 getCouponAmount 升级为 getActualCouponDeduct 兜底机制
                // ==========================================
                BigDecimal actualCoupon = orderVO.getActualCouponDeduct() != null ? orderVO.getActualCouponDeduct() : orderVO.getCouponAmount();
                if (actualCoupon != null && actualCoupon.compareTo(BigDecimal.ZERO) > 0) {
                    writeText(bos, "会员扣券: " + fmt(actualCoupon) + "\n");
                }

                if (orderVO.getUseVoucherAmount() != null && orderVO.getUseVoucherAmount().compareTo(BigDecimal.ZERO) > 0) {
                    writeText(bos, "满减抵扣: " + fmt(orderVO.getUseVoucherAmount()) + "\n");
                }

                writeText(bos, "--------------------------------\n");
                if (orderVO.getMemberInfo() != null) {
                    writeText(bos, "会员姓名: " + orderVO.getMemberInfo().getName() + "\n");
                    if (orderVO.getMemberInfo().getCoupon() != null) {
                        writeText(bos, "会员券余额: " + fmt(orderVO.getMemberInfo().getCoupon()) + "\n");
                    }
                    if (orderVO.getMemberId() != null) {
                        Long vCount = memberCouponCountQuery.countUnusedCouponsByMemberIds(
                                java.util.Collections.singletonList(orderVO.getMemberId()))
                                .get(orderVO.getMemberId());
                        if (vCount != null && vCount > 0) {
                            writeText(bos, "满减券余量: " + vCount + " 张\n");
                        }
                    }
                }
            }

            // 6. 尾部信息
            writeText(bos, "--------------------------------\n");
            bos.write(EscPosUtil.ALIGN_LEFT);
            if (StringUtils.hasText(config.getShopPhone())) {
                writeText(bos, "门店热线: " + config.getShopPhone() + "\n");
            }
            if (StringUtils.hasText(config.getShopAddress())) {
                writeText(bos, "门店地址: " + config.getShopAddress() + "\n");
            }

            bos.write(EscPosUtil.ALIGN_CENTER);
            if (StringUtils.hasText(config.getFooterMsg())) {
                writeText(bos, "\n" + config.getFooterMsg() + "\n");
            }

            writeText(bos, "\n\n\n\n\n");
            executeHardwareCommand(bos.toByteArray());

        } catch (Exception e) {
            log.error("❌ 硬件打印指令执行失败: ", e);
        }
    }

    /** 收银台手动开箱，不依赖“现金收款后自动弹开”配置。 */
    public void openCashDrawer() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(EscPosUtil.INIT);
            bos.write(EscPosUtil.OPEN_CASH_DRAWER);
            executeHardwareCommand(bos.toByteArray());
        } catch (Exception e) {
            log.error("❌ 钱箱开启指令执行失败: ", e);
        }
    }

    // ==========================================
    // 🌟 核心新增：桌面端原生的交接班 58mm 小票 ESC/POS 硬件打印
    // ==========================================
    public void printShiftHandover(FinanceDataVO.ShiftHandoverVO vo) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(EscPosUtil.INIT);

            // 头部
            bos.write(EscPosUtil.ALIGN_CENTER);
            bos.write(EscPosUtil.BOLD_ON);
            bos.write(EscPosUtil.TEXT_LARGE);
            writeText(bos, "门店交接班小票\n");
            bos.write(EscPosUtil.TEXT_NORMAL);
            bos.write(EscPosUtil.BOLD_OFF);
            writeText(bos, "打印: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
            writeText(bos, "--------------------------------\n");

            // 基本信息
            bos.write(EscPosUtil.ALIGN_LEFT);
            writeText(bos, "收银员: " + vo.getCashierName() + "\n");
            writeText(bos, "接班: " + vo.getShiftStartTime() + "\n");
            writeText(bos, "交班: " + vo.getShiftEndTime() + "\n");
            writeText(bos, "--------------------------------\n");

            // 核心应缴
            bos.write(EscPosUtil.ALIGN_CENTER);
            writeText(bos, "【本班应缴现额】\n");
            bos.write(EscPosUtil.BOLD_ON);
            bos.write(EscPosUtil.TEXT_LARGE);
            writeText(bos, "￥ " + fmt(vo.getExpectedTotalIncome()) + "\n");
            bos.write(EscPosUtil.TEXT_NORMAL);
            bos.write(EscPosUtil.BOLD_OFF);
            writeText(bos, "(需核对抽屉与扫码实收)\n");
            writeText(bos, "--------------------------------\n");

            // 流水账目
            bos.write(EscPosUtil.ALIGN_LEFT);
            writeText(bos, "【实收流水明细】\n");
            writeText(bos, formatLineSpace("现金支付", fmt(vo.getCashPay())));
            writeText(bos, formatLineSpace("聚合扫码", fmt(vo.getScanPay())));

            if (vo.getScanPayBreakdown() != null && !vo.getScanPayBreakdown().isEmpty()) {
                for (FinanceDataVO.PayPieData pie : vo.getScanPayBreakdown()) {
                    // 后端实时解析渠道中文标签，确保打印机不打出拼音
                    String tagName = resolvePayTagName(pie.getName());
                    writeText(bos, formatLineSpace(" -" + tagName, fmt(pie.getValue())));
                }
            }
            writeText(bos, formatLineSpace("会员余额", fmt(vo.getBalancePay())));
            writeText(bos, "--------------------------------\n");

            // 退款与净收
            writeText(bos, "【退款与净收核算】\n");
            writeText(bos, formatLineSpace("退款冲回", "-" + fmt(vo.getRefundAmount())));
            bos.write(EscPosUtil.BOLD_ON);
            writeText(bos, formatLineSpace("全渠道净收总计", fmt(vo.getNetIncome())));
            bos.write(EscPosUtil.BOLD_OFF);
            writeText(bos, "--------------------------------\n");

            // 营销折让
            writeText(bos, "【营销与资产核销】\n");
            writeText(bos, formatLineSpace("会员券真实核销", fmt(vo.getMemberCouponPay())));
            writeText(bos, formatLineSpace("满减抵扣(" + vo.getVoucherCount() + "张)", fmt(vo.getVoucherDiscount())));
            writeText(bos, formatLineSpace("店铺免券让利", fmt(vo.getWaivedCouponAmount())));
            writeText(bos, formatLineSpace("手工整单抹零", fmt(vo.getManualDiscount())));
            writeText(bos, "--------------------------------\n\n");

            // 尾部签字
            writeText(bos, "交班签字: ________________\n\n");
            writeText(bos, "接班签字: ________________\n\n");
            bos.write(EscPosUtil.ALIGN_CENTER);
            writeText(bos, "- 辛苦了 -\n\n\n\n\n");

            // 发送给打印机！
            executeHardwareCommand(bos.toByteArray());

        } catch (Exception e) {
            log.error("❌ 打印交接班小票失败", e);
        }
    }

    private String resolvePayName(OrderDetailVO.OrderPayVO pay) {
        if (StringUtils.hasText(pay.getPayTag())) {
            SysDictDetail subDetail = sysDictDetailMapper.selectOne(new LambdaQueryWrapper<SysDictDetail>().eq(SysDictDetail::getDict, "paySubTag").eq(SysDictDetail::getValue, pay.getPayTag()).last("LIMIT 1"));
            if (subDetail != null && StringUtils.hasText(subDetail.getCnDesc())) return subDetail.getCnDesc();
        }
        if (StringUtils.hasText(pay.getPayMethodCode())) {
            SysDictDetail mainDetail = sysDictDetailMapper.selectOne(new LambdaQueryWrapper<SysDictDetail>().eq(SysDictDetail::getDict, "pos_payment_method").eq(SysDictDetail::getValue, pay.getPayMethodCode()).last("LIMIT 1"));
            if (mainDetail != null && StringUtils.hasText(mainDetail.getCnDesc())) return mainDetail.getCnDesc();
        }
        String fallbackName = pay.getPayMethodName();
        if (StringUtils.hasText(fallbackName)) return fallbackName.replace("其他渠道", "").replace("(", "").replace(")", "").trim();
        return "扫码支付";
    }

    // 🌟 后端反解析标签，专供交接班聚合扫码分类使用
    private String resolvePayTagName(String code) {
        if (!StringUtils.hasText(code) || "UNKNOWN".equals(code)) return "未分类扫码";
        SysDictDetail detail = sysDictDetailMapper.selectOne(new LambdaQueryWrapper<SysDictDetail>().eq(SysDictDetail::getDict, "paySubTag").eq(SysDictDetail::getValue, code).last("LIMIT 1"));
        return detail != null && StringUtils.hasText(detail.getCnDesc()) ? detail.getCnDesc() : code;
    }

    // ==========================================
    // 基础排版工具
    // ==========================================
    private String fmt(BigDecimal amt) {
        if (amt == null) return "0.00";
        return amt.setScale(2, RoundingMode.HALF_UP).toString();
    }

    private void writeText(ByteArrayOutputStream bos, String text) throws IOException {
        bos.write(text.getBytes("GBK"));
    }

    private void executeHardwareCommand(byte[] printData) throws PrintException {
        PrintService printService = PrintServiceLookup.lookupDefaultPrintService();
        if (printService == null) return;
        DocPrintJob job = printService.createPrintJob();
        Doc doc = new SimpleDoc(printData, DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
        job.print(doc, null);
    }

    private String formatItemLine(String orig, String curr, String qty, String disc, String subtotal) {
        return padRight(orig, 6) + padRight(curr, 6) + padRight(qty, 5) + padRight(disc, 6) + padLeft(subtotal, 9);
    }

    // 🌟 专供交接班的左右两端对齐排版器 (基于 58mm 纸张宽度 32 字符计算)
    private String formatLineSpace(String left, String right) {
        int totalLen = 32;
        int leftLen = getStringLength(left);
        int rightLen = getStringLength(right);
        int spaces = totalLen - leftLen - rightLen;
        if (spaces < 1) spaces = 1;
        StringBuilder sb = new StringBuilder(left);
        for(int i=0; i<spaces; i++) sb.append(" ");
        sb.append(right).append("\n");
        return sb.toString();
    }

    private String padRight(String str, int length) {
        int strLen = getStringLength(str);
        if (strLen >= length) return str;
        StringBuilder sb = new StringBuilder(str);
        for (int i = 0; i < length - strLen; i++) sb.append(" ");
        return sb.toString();
    }

    private String padLeft(String str, int length) {
        int strLen = getStringLength(str);
        if (strLen >= length) return str;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length - strLen; i++) sb.append(" ");
        sb.append(str);
        return sb.toString();
    }

    private int getStringLength(String str) {
        if (str == null) return 0;
        int len = 0;
        for (char c : str.toCharArray()) {
            len += (c > 255) ? 2 : 1;
        }
        return len;
    }
}
