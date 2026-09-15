package com.money.feature.trade.application.pos;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.constant.CouponStatusEnum;
import com.money.dto.pos.*;
import com.money.entity.*;
import com.money.mapper.*;
import com.money.service.*;
import com.money.feature.trade.application.checkout.CheckoutOrchestrator;
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

    private final UmsMemberService umsMemberService;
    private final GmsGoodsService gmsGoodsService;
    private final PosSkuLevelPriceMapper posSkuLevelPriceMapper;
    private final PosCouponRuleMapper posCouponRuleMapper;
    private final PosMemberCouponMapper posMemberCouponMapper;
    private final UmsMemberBrandLevelMapper umsMemberBrandLevelMapper;
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
        List<GmsGoods> gmsGoodsList = gmsGoodsService.lambdaQuery()
                .and(StrUtil.isNotBlank(barcode), w ->
                        w.like(GmsGoods::getBarcode, barcode)
                                .or().like(GmsGoods::getName, barcode)
                                .or().like(GmsGoods::getMnemonicCode, barcode.toUpperCase())
                ).list();

        List<PosGoodsVO> posGoodsVOS = BeanMapUtil.to(gmsGoodsList, PosGoodsVO::new);

        if (!posGoodsVOS.isEmpty()) {
            List<Long> skuIds = posGoodsVOS.stream().map(PosGoodsVO::getId).collect(Collectors.toList());
            List<PosSkuLevelPrice> levelPrices = posSkuLevelPriceMapper.selectList(new LambdaQueryWrapper<PosSkuLevelPrice>().in(PosSkuLevelPrice::getSkuId, skuIds));
            Map<Long, List<PosSkuLevelPrice>> skuPriceMap = levelPrices.stream().collect(Collectors.groupingBy(PosSkuLevelPrice::getSkuId));

            for (PosGoodsVO vo : posGoodsVOS) {
                Map<String, BigDecimal> priceMap = new HashMap<>();
                Map<String, BigDecimal> couponMap = new HashMap<>();
                List<PosSkuLevelPrice> prices = skuPriceMap.get(vo.getId());
                if (prices != null) {
                    for (PosSkuLevelPrice p : prices) {
                        priceMap.put(p.getLevelId(), p.getMemberPrice());
                        couponMap.put(p.getLevelId(), p.getMemberCoupon() != null ? p.getMemberCoupon() : BigDecimal.ZERO);
                    }
                }
                vo.setLevelPrices(priceMap);
                vo.setLevelCoupons(couponMap);
            }
        }
        return posGoodsVOS;
    }

    @Override
    public List<PosMemberVO> listMember(String member) {
        List<UmsMember> memberList = umsMemberService.lambdaQuery().eq(UmsMember::getDeleted, false)
                .and(StrUtil.isNotBlank(member), w -> w.like(UmsMember::getName, member).or().like(UmsMember::getPhone, member)).list();
        List<PosMemberVO> posMemberVOS = BeanMapUtil.to(memberList, PosMemberVO::new);

        if (!posMemberVOS.isEmpty()) {
            List<Long> memberIds = posMemberVOS.stream().map(PosMemberVO::getId).collect(Collectors.toList());
            List<UmsMemberBrandLevel> allBrandLevels = umsMemberBrandLevelMapper.selectList(
                    new LambdaQueryWrapper<UmsMemberBrandLevel>().in(UmsMemberBrandLevel::getMemberId, memberIds)
            );
            Map<Long, List<UmsMemberBrandLevel>> blMap = allBrandLevels.stream().collect(Collectors.groupingBy(UmsMemberBrandLevel::getMemberId));

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
                List<UmsMemberBrandLevel> levels = blMap.get(vo.getId());
                Map<String, String> levelMap = new HashMap<>();      // 存旧数据 (防报错)
                Map<String, String> levelDescMap = new HashMap<>();  // 存新语义 (纯中文)

                if (levels != null) {
                    for (UmsMemberBrandLevel bl : levels) {
                        levelMap.put(bl.getBrand(), bl.getLevelCode());

                        // 🌟 执行“双擎翻译”
                        String brandName = brandMap.getOrDefault(bl.getBrand(), "未知品牌(" + bl.getBrand() + ")");
                        String safeLevelCode = bl.getLevelCode() != null ? bl.getLevelCode().trim().toUpperCase() : "";
                        String levelName = levelDictMap.getOrDefault(safeLevelCode, bl.getLevelCode());

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
    public List<PosCouponRule> getValidCouponRules() {
        return posCouponRuleMapper.selectList(new LambdaQueryWrapper<PosCouponRule>().orderByDesc(PosCouponRule::getId));
    }

    @Override
    public SettleResultVO settleAccounts(SettleAccountsDTO dto) {
        return checkoutOrchestrator.orchestrate(dto);
    }
}
