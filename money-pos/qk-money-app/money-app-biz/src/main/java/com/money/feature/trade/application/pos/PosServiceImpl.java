package com.money.feature.trade.application.pos;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.constant.CouponStatusEnum;
import com.money.contract.member.MemberPosProfileQuery;
import com.money.contract.member.MemberPosProfileSnapshot;
import com.money.contract.goods.PosGoodsCatalogQuery;
import com.money.contract.goods.PosGoodsCatalogSnapshot;
import com.money.dto.pos.*;
import com.money.entity.GmsBrand;
import com.money.entity.PosCouponRule;
import com.money.entity.PosMemberCoupon;
import com.money.entity.SysDictDetail;
import com.money.mapper.*;
import com.money.service.*;
import com.money.feature.gms.application.catalog.GmsBrandService;
import com.money.feature.trade.application.checkout.CheckoutOrchestrator;
import com.money.feature.trade.application.pos.dto.CouponRuleSummary;
import com.money.web.util.BeanMapUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PosServiceImpl implements PosService {

    private final MemberPosProfileQuery memberPosProfileQuery;
    private final PosGoodsCatalogQuery posGoodsCatalogQuery;
    private final PosCouponRuleMapper posCouponRuleMapper;
    private final PosMemberCouponMapper posMemberCouponMapper;
    private final CheckoutOrchestrator checkoutOrchestrator;

    // 🌟 核心新增：注入双擎翻译服务
    private final SysDictDetailService sysDictDetailService;
    private final GmsBrandService gmsBrandService;

    /**
     * 🌟 翻译引擎 1：加载全量品牌映射 (ID -> 名称)
     */
    private Map<String, String> getBrandMap() {
        Map<String, String> map = new HashMap<>();
        try {
            List<GmsBrand> brands = gmsBrandService.list();
            if (brands != null) {
                for (GmsBrand b : brands) {
                    map.put(String.valueOf(b.getId()), b.getName());
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ 获取品牌映射表失败", e);
        }
        return map;
    }

    /**
     * 🌟 翻译引擎 2：加载会员等级字典 (Code -> 中文名)
     */
    private Map<String, String> getMemberLevelDictMap() {
        Map<String, String> map = new HashMap<>();
        try {
            List<SysDictDetail> details = sysDictDetailService.listByDict("memberType");
            if (details != null) {
                for (SysDictDetail d : details) {
                    if (StrUtil.isNotBlank(d.getValue())) {
                        map.put(d.getValue().trim().toUpperCase(), d.getCnDesc());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ 获取 memberType 字典失败", e);
        }
        return map;
    }

    @Override
    public List<PosGoodsVO> listGoods(String barcode) {
        List<PosGoodsCatalogSnapshot> goods = posGoodsCatalogQuery.searchForPos(barcode);
        return BeanMapUtil.to(goods, PosGoodsVO::new);
    }

    @Override
    public List<PosMemberVO> listMember(String member) {
        List<MemberPosProfileSnapshot> memberList = memberPosProfileQuery.searchActiveMembers(member);
        List<PosMemberVO> posMemberVOS = BeanMapUtil.to(memberList, PosMemberVO::new);

        if (!posMemberVOS.isEmpty()) {
            List<Long> memberIds = posMemberVOS.stream().map(PosMemberVO::getId).collect(Collectors.toList());
            List<PosMemberCoupon> allUnusedCoupons = posMemberCouponMapper.selectList(
                    new LambdaQueryWrapper<PosMemberCoupon>().in(PosMemberCoupon::getMemberId, memberIds).eq(PosMemberCoupon::getStatus, CouponStatusEnum.UNUSED.name())
            );

            final Map<Long, PosCouponRule> ruleMap = new HashMap<>();
            if (!allUnusedCoupons.isEmpty()) {
                List<Long> ruleIds = allUnusedCoupons.stream().map(PosMemberCoupon::getRuleId).distinct().collect(Collectors.toList());
                ruleMap.putAll(posCouponRuleMapper.selectBatchIds(ruleIds).stream().collect(Collectors.toMap(PosCouponRule::getId, rule -> rule)));
            }

            // 🌟 核心防爆破：一次性加载双擎缓存，避免在 for 循环中查库
            Map<String, String> brandMap = getBrandMap();
            Map<String, String> levelDictMap = getMemberLevelDictMap();

            for (PosMemberVO vo : posMemberVOS) {
                Map<String, String> levelMap = vo.getBrandLevels() != null
                        ? new HashMap<>(vo.getBrandLevels()) : new HashMap<>();
                Map<String, String> levelDescMap = new HashMap<>();  // 存新语义 (纯中文)

                if (!levelMap.isEmpty()) {
                    for (Map.Entry<String, String> level : levelMap.entrySet()) {
                        // 🌟 执行“双擎翻译”
                        String brandName = brandMap.getOrDefault(level.getKey(), "未知品牌(" + level.getKey() + ")");
                        String safeLevelCode = level.getValue() != null ? level.getValue().trim().toUpperCase() : "";
                        String levelName = levelDictMap.getOrDefault(safeLevelCode, level.getValue());

                        levelDescMap.put(brandName, levelName);
                    }
                }
                vo.setBrandLevels(levelMap);
                vo.setBrandLevelDesc(levelDescMap); // 🌟 挂载纯中文语义矩阵

                List<PosMemberCoupon> hisCoupons = allUnusedCoupons.stream().filter(c -> c.getMemberId().equals(vo.getId())).collect(Collectors.toList());
                vo.setVoucherCount(hisCoupons.size());

                if (!hisCoupons.isEmpty()) {
                    Map<Long, Long> ruleCountMap = hisCoupons.stream().collect(Collectors.groupingBy(PosMemberCoupon::getRuleId, Collectors.counting()));
                    List<PosMemberVO.MemberCouponRuleVO> ruleVOList = ruleCountMap.entrySet().stream().filter(entry -> ruleMap.containsKey(entry.getKey())).map(entry -> {
                        PosCouponRule rule = ruleMap.get(entry.getKey());
                        PosMemberVO.MemberCouponRuleVO ruleVO = new PosMemberVO.MemberCouponRuleVO();
                        ruleVO.setRuleId(rule.getId());
                        ruleVO.setName("满" + rule.getThresholdAmount().stripTrailingZeros().toPlainString() + "减" + rule.getDiscountAmount().stripTrailingZeros().toPlainString());
                        ruleVO.setThreshold(rule.getThresholdAmount());
                        ruleVO.setDeduction(rule.getDiscountAmount());
                        ruleVO.setAvailableCount(entry.getValue().intValue());
                        return ruleVO;
                    }).collect(Collectors.toList());
                    vo.setCouponList(ruleVOList);
                }
            }
        }
        return posMemberVOS;
    }

    @Override
    public List<CouponRuleSummary> getValidCouponRules() {
        return posCouponRuleMapper.selectList(new LambdaQueryWrapper<PosCouponRule>().orderByDesc(PosCouponRule::getId))
                .stream()
                .map(rule -> new CouponRuleSummary(
                        rule.getId(), rule.getName(), rule.getThresholdAmount(), rule.getDiscountAmount(), rule.getStatus()))
                .collect(Collectors.toList());
    }

    @Override
    public SettleResultVO settleAccounts(SettleAccountsDTO dto) {
        return checkoutOrchestrator.orchestrate(dto);
    }
}
