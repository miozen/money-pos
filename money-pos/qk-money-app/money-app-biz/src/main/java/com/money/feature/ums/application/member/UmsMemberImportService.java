package com.money.feature.ums.application.member;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.goods.BrandSelectionQuery;
import com.money.contract.goods.BrandSelectionSnapshot;
import com.money.entity.PosMemberCoupon;
import com.money.entity.UmsMember;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberBrandLevel;
import com.money.entity.UmsMemberLog;
import com.money.mapper.PosMemberCouponMapper;
import com.money.mapper.UmsMemberBrandLevelMapper;
import com.money.mapper.UmsMemberLogMapper;
import com.money.mapper.UmsMemberMapper;
import com.money.service.SysDictDetailService;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 领域服务：会员导入与发券子域
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UmsMemberImportService {

    private final UmsMemberMapper umsMemberMapper;
    private final UmsMemberBrandLevelMapper umsMemberBrandLevelMapper;
    private final PosMemberCouponMapper posMemberCouponMapper;
    private final BrandSelectionQuery brandSelectionQuery;
    private final SysDictDetailService sysDictDetailService;
    private final UmsMemberLogMapper umsMemberLogMapper;

    private static final String STATUS_UNUSED = "UNUSED";
    private static final String TYPE_BALANCE = "BALANCE";
    private static final String TYPE_COUPON = "COUPON";
    private static final String TYPE_VOUCHER = "VOUCHER";

    @Transactional(rollbackFor = Exception.class)
    public void batchIssueVoucher(List<Long> memberIds, Long ruleId, Integer quantity) {
        if (memberIds == null || memberIds.isEmpty() || ruleId == null || quantity == null || quantity <= 0) {
            throw new BaseException("批量发券参数异常");
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long memberId : memberIds) {
            for (int i = 0; i < quantity; i++) {
                PosMemberCoupon pc = new PosMemberCoupon();
                pc.setMemberId(memberId);
                pc.setRuleId(ruleId);
                pc.setStatus(STATUS_UNUSED);
                pc.setGetTime(now);
                posMemberCouponMapper.insert(pc);
            }
        }
    }

    // 🌟 核心升级：返回 String 类型的战报
    @SneakyThrows
    @Transactional(rollbackFor = Exception.class)
    public String importMembers(MultipartFile file) {
        log.info("开始执行动态智能 Excel 老会员导入引擎...");

        Map<String, String> dictReverseMap = new HashMap<>();
        for (Map.Entry<String, String> dict : sysDictDetailService.getValueToCnDescMap("memberType").entrySet()) {
            dictReverseMap.put(dict.getValue(), dict.getKey());
        }

        Map<String, String> brandName2IdMap = new HashMap<>();
        for (BrandSelectionSnapshot brand : brandSelectionQuery.listBrandSelections()) {
            brandName2IdMap.put(brand.getName(), String.valueOf(brand.getId()));
        }

        List<UmsMember> parsedMembers = new ArrayList<>();
        Map<String, Map<String, String>> memberBrandLevelDataMap = new HashMap<>();
        Map<Integer, String> brandColMap = new HashMap<>();
        Map<String, Integer> importVoucherMap = new HashMap<>();

        // 🌟 探针1：记录跳过的空行或无效数据
        int[] skipCount = new int[]{0};

        EasyExcel.read(file.getInputStream(), new AnalysisEventListener<Map<Integer, String>>() {
            @Override
            public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
                for (Map.Entry<Integer, String> entry : headMap.entrySet()) {
                    String headName = entry.getValue();
                    if (headName != null && headName.startsWith("[品牌特权] ")) {
                        String brandId = brandName2IdMap.get(headName.replace("[品牌特权] ", "").trim());
                        if (StrUtil.isNotBlank(brandId)) brandColMap.put(entry.getKey(), brandId);
                    }
                }
            }

            @Override
            public void invoke(Map<Integer, String> data, AnalysisContext context) {
                String name = data.get(0);
                String phone = data.get(1);

                // 🌟 拦截：如果没有姓名或手机号，直接跳过并计数
                if (StrUtil.isBlank(name) || StrUtil.isBlank(phone)) {
                    skipCount[0]++;
                    return;
                }

                UmsMember member = new UmsMember();
                member.setName(name);
                member.setPhone(phone);
                member.setType("MEMBER");
                member.setConsumeAmount(BigDecimal.ZERO);
                member.setConsumeCoupon(BigDecimal.ZERO);
                member.setConsumeTimes(0);
                member.setCancelTimes(0);
                member.setBalance(new BigDecimal(StrUtil.isNotBlank(data.get(2)) ? data.get(2) : "0"));
                member.setCoupon(new BigDecimal(StrUtil.isNotBlank(data.get(3)) ? data.get(3) : "0"));
                parsedMembers.add(member);

                String voucherStr = data.get(4);
                if (StrUtil.isNotBlank(voucherStr)) {
                    int vCount = Integer.parseInt(voucherStr.trim());
                    if (vCount > 0) importVoucherMap.put(phone, vCount);
                }

                Map<String, String> currentBrandLevels = new HashMap<>();
                for (Map.Entry<Integer, String> brandCol : brandColMap.entrySet()) {
                    String excelCnLevel = data.get(brandCol.getKey());
                    if (StrUtil.isNotBlank(excelCnLevel)) {
                        String sysLevelCode = dictReverseMap.get(excelCnLevel.trim());
                        if (StrUtil.isNotBlank(sysLevelCode)) {
                            currentBrandLevels.put(brandCol.getValue(), sysLevelCode);
                        }
                    }
                }
                memberBrandLevelDataMap.put(phone, currentBrandLevels);
            }
            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {}
        }).sheet().doRead();

        if (parsedMembers.isEmpty()) {
            return "未发现有效数据。若有空行，已自动跳过 " + skipCount[0] + " 条。";
        }

        LocalDateTime now = LocalDateTime.now();
        Long defaultVoucherRuleId = 1L;

        // 🌟 探针2：记录新增和更新的数量
        int insertCount = 0;
        int updateCount = 0;

        for (UmsMember m : parsedMembers) {
            UmsMember existMember = umsMemberMapper.selectOne(new LambdaQueryWrapper<UmsMember>().eq(UmsMember::getPhone, m.getPhone()).last("LIMIT 1"));
            Long targetMemberId;
            UmsMember targetLogMember;

            if (existMember != null) {
                BigDecimal addBal = m.getBalance() != null ? m.getBalance() : BigDecimal.ZERO;
                BigDecimal addCou = m.getCoupon() != null ? m.getCoupon() : BigDecimal.ZERO;
                existMember.setBalance((existMember.getBalance() != null ? existMember.getBalance() : BigDecimal.ZERO).add(addBal));
                existMember.setCoupon((existMember.getCoupon() != null ? existMember.getCoupon() : BigDecimal.ZERO).add(addCou));

                // ==========================================
                // 🌟 核心修复：亡者复苏！
                // 解除软删除标记，让这名老会员重新回到页面列表中！
                // ==========================================
                existMember.setDeleted(false); // 设置为 false 代表未被删除

                umsMemberMapper.updateById(existMember);
                targetMemberId = existMember.getId();
                targetLogMember = existMember;
                updateCount++; // 累加更新数

                Map<String, String> importedLevels = memberBrandLevelDataMap.get(m.getPhone());
                if (importedLevels != null && !importedLevels.isEmpty()) {
                    List<UmsMemberBrandLevel> existLevels = umsMemberBrandLevelMapper.selectList(new LambdaQueryWrapper<UmsMemberBrandLevel>().eq(UmsMemberBrandLevel::getMemberId, existMember.getId()));
                    Map<String, String> mergedLevels = new HashMap<>();
                    if (existLevels != null) {
                        for (UmsMemberBrandLevel bl : existLevels) mergedLevels.put(bl.getBrand(), bl.getLevelCode());
                    }
                    mergedLevels.putAll(importedLevels);
                    saveBrandLevels(existMember.getId(), mergedLevels);
                }

                if (addBal.compareTo(BigDecimal.ZERO) > 0) {
                    umsMemberLogMapper.insert(createLog(existMember, TYPE_BALANCE, "IMPORT", addBal, addBal, existMember.getBalance(), "SYS_IMPORT", "老客户再次导入叠加余额"));
                }
                if (addCou.compareTo(BigDecimal.ZERO) > 0) {
                    umsMemberLogMapper.insert(createLog(existMember, TYPE_COUPON, "IMPORT", addCou, BigDecimal.ZERO, existMember.getCoupon(), "SYS_IMPORT", "老客户再次导入叠加券额"));
                }

            } else {
                String newCode;
                do { newCode = RandomUtil.randomNumbers(8); } while (umsMemberMapper.exists(new LambdaQueryWrapper<UmsMember>().eq(UmsMember::getCode, newCode)));
                m.setCode(newCode);

                // 确保新插入的数据绝对不会处于删除状态
                m.setDeleted(false);

                umsMemberMapper.insert(m);
                targetMemberId = m.getId();
                targetLogMember = m;
                insertCount++; // 累加新增数

                saveBrandLevels(m.getId(), memberBrandLevelDataMap.get(m.getPhone()));

                if (m.getBalance() != null && m.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                    umsMemberLogMapper.insert(createLog(m, TYPE_BALANCE, "IMPORT", m.getBalance(), m.getBalance(), m.getBalance(), "SYS_IMPORT", "新客导入初始余额"));
                }
                if (m.getCoupon() != null && m.getCoupon().compareTo(BigDecimal.ZERO) > 0) {
                    umsMemberLogMapper.insert(createLog(m, TYPE_COUPON, "IMPORT", m.getCoupon(), BigDecimal.ZERO, m.getCoupon(), "SYS_IMPORT", "新客导入初始券额"));
                }
            }

            Integer vCount = importVoucherMap.get(m.getPhone());
            if (vCount != null && vCount > 0) {
                for (int i = 0; i < vCount; i++) {
                    PosMemberCoupon pc = new PosMemberCoupon();
                    pc.setMemberId(targetMemberId);
                    pc.setRuleId(defaultVoucherRuleId);
                    pc.setStatus(STATUS_UNUSED);
                    pc.setGetTime(now);
                    posMemberCouponMapper.insert(pc);
                }
                umsMemberLogMapper.insert(createLog(targetLogMember, TYPE_VOUCHER, "IMPORT", BigDecimal.valueOf(vCount), BigDecimal.ZERO, BigDecimal.valueOf(vCount), "SYS_IMPORT", "导入初始满减券"));
            }
        }

        // 🌟 核心：组合漂亮的战报话术返回
        String resultMsg = String.format("🎉 会员导入完成！成功新增 %d 位，更新 %d 位老会员资产。", insertCount, updateCount);
        if (skipCount[0] > 0) {
            resultMsg += String.format(" 发现并自动跳过 %d 条无效数据(缺少姓名或手机号)。", skipCount[0]);
        }

        log.info(resultMsg);
        return resultMsg;
    }

    private UmsMemberLog createLog(UmsMember m, String type, String opType, BigDecimal amt, BigDecimal realAmt, BigDecimal afterAmt, String orderNo, String remark) {
        UmsMemberLog l = new UmsMemberLog();
        l.setMemberId(m.getId());
        l.setMemberName(m.getName());
        l.setMemberPhone(m.getPhone());
        l.setType(type);
        l.setOperateType(opType);
        l.setAmount(amt);
        l.setRealAmount(realAmt);
        l.setAfterAmount(afterAmt);
        l.setOrderNo(orderNo);
        l.setRemark(remark);
        l.setCreateTime(LocalDateTime.now());
        return l;
    }

    private void saveBrandLevels(Long memberId, Map<String, String> brandLevels) {
        umsMemberBrandLevelMapper.delete(new LambdaQueryWrapper<UmsMemberBrandLevel>().eq(UmsMemberBrandLevel::getMemberId, memberId));
        if (brandLevels != null) {
            brandLevels.forEach((brandIdStr, level) -> {
                if (StrUtil.isNotBlank(level)) {
                    UmsMemberBrandLevel bl = new UmsMemberBrandLevel();
                    bl.setMemberId(memberId);
                    bl.setBrand(brandIdStr);
                    bl.setLevelCode(level);
                    umsMemberBrandLevelMapper.insert(bl);
                }
            });
        }
    }
}
