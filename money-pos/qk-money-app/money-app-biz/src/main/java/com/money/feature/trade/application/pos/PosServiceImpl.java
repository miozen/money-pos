package com.money.feature.trade.application.pos;

import cn.hutool.core.util.StrUtil;
import com.money.contract.goods.BrandNameQuery;
import com.money.contract.member.MemberPosProfileQuery;
import com.money.contract.member.MemberPosProfileSnapshot;
import com.money.contract.member.PosMemberBenefitQuery;
import com.money.contract.member.PosMemberBenefitSnapshot;
import com.money.contract.goods.PosGoodsCatalogQuery;
import com.money.contract.goods.PosGoodsCatalogSnapshot;
import com.money.dto.pos.*;
import com.money.service.SysDictDetailService;
import com.money.feature.trade.application.checkout.CheckoutOrchestrator;
import com.money.feature.trade.application.pos.dto.CouponRuleSummary;
import com.money.web.util.BeanMapUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PosServiceImpl implements PosService {

    private final MemberPosProfileQuery memberPosProfileQuery;
    private final PosGoodsCatalogQuery posGoodsCatalogQuery;
    private final PosMemberBenefitQuery posMemberBenefitQuery;
    private final BrandNameQuery brandNameQuery;
    private final CheckoutOrchestrator checkoutOrchestrator;

    // 🌟 核心新增：注入双擎翻译服务
    private final SysDictDetailService sysDictDetailService;

    private Map<String, String> getBrandMap(Set<String> brandIds) {
        try {
            return brandNameQuery.findNamesByIds(brandIds);
        } catch (Exception e) {
            log.warn("⚠️ 获取品牌映射表失败", e);
            return new HashMap<>();
        }
    }
    /**
     * 🌟 翻译引擎 2：加载会员等级字典 (Code -> 中文名)
     */
    private Map<String, String> getMemberLevelDictMap() {
        Map<String, String> map = new HashMap<>();
        try {
            Map<String, String> details = sysDictDetailService.getValueToCnDescMap("memberType");
            if (details != null) {
                for (Map.Entry<String, String> detail : details.entrySet()) {
                    if (StrUtil.isNotBlank(detail.getKey())) {
                        map.put(detail.getKey().trim().toUpperCase(), detail.getValue());
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
            Map<Long, PosMemberBenefitSnapshot> benefitsByMember = posMemberBenefitQuery.findForMembers(memberIds);
            Map<String, String> brandMap = getBrandMap(posMemberVOS.stream()
                    .flatMap(vo -> vo.getBrandLevels() == null ? java.util.stream.Stream.<String>empty()
                            : vo.getBrandLevels().keySet().stream())
                    .collect(Collectors.toSet()));
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

                PosMemberBenefitSnapshot benefit = benefitsByMember.get(vo.getId());
                vo.setVoucherCount(benefit == null ? 0 : benefit.getVoucherCount());

                if (benefit != null && !benefit.getCouponRules().isEmpty()) {
                    List<PosMemberVO.MemberCouponRuleVO> ruleVOList = benefit.getCouponRules().stream().map(rule -> {
                        PosMemberVO.MemberCouponRuleVO ruleVO = new PosMemberVO.MemberCouponRuleVO();
                        ruleVO.setRuleId(rule.getId());
                        ruleVO.setName("满" + rule.getThresholdAmount().stripTrailingZeros().toPlainString() + "减" + rule.getDiscountAmount().stripTrailingZeros().toPlainString());
                        ruleVO.setThreshold(rule.getThresholdAmount());
                        ruleVO.setDeduction(rule.getDiscountAmount());
                        ruleVO.setAvailableCount(rule.getAvailableCount());
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
        return posMemberBenefitQuery.listCouponRules()
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
