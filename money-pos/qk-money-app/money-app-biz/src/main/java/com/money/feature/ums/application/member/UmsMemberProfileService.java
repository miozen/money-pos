package com.money.feature.ums.application.member;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.money.constant.BizErrorStatus;
import com.money.contract.trade.MemberOrderExistenceQuery;
import com.money.dto.UmsMember.UmsMemberDTO;
import com.money.dto.UmsMember.UmsMemberQueryDTO;
import com.money.dto.UmsMember.UmsMemberVO;
import com.money.contract.goods.BrandNameQuery;
import com.money.feature.ums.application.cloudsync.MemberCloudSyncOutboxService;
import com.money.feature.ums.infrastructure.persistence.entity.PosMemberCoupon;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMember;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberBrandLevel;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberAmountRight;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberQuantityRight;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberTargetPlan;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberLog;
import com.money.feature.ums.infrastructure.persistence.entity.UmsRechargeOrder;
import com.money.feature.ums.infrastructure.persistence.mapper.UmsMemberAmountRightMapper;
import com.money.feature.ums.infrastructure.persistence.mapper.UmsMemberQuantityRightMapper;
import com.money.feature.ums.infrastructure.persistence.mapper.UmsMemberTargetPlanMapper;
import com.money.mapper.PosMemberCouponMapper;
import com.money.mapper.UmsMemberBrandLevelMapper;
import com.money.mapper.UmsMemberLogMapper;
import com.money.mapper.UmsMemberMapper;
import com.money.mapper.UmsRechargeOrderMapper;
import com.money.dto.UmsMember.MemberGoodsRankVO;
import com.money.service.SysDictDetailService;
import com.money.util.PageUtil;
import com.money.web.exception.BaseException;
import com.money.web.vo.PageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 领域服务：会员档案与查询子域
 * 职责：负责会员的基础资料增删改查、多品牌等级挂载、排行榜及沉睡分析
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UmsMemberProfileService {

    private final UmsMemberMapper umsMemberMapper;
    private final PosMemberCouponMapper posMemberCouponMapper;
    private final UmsMemberBrandLevelMapper umsMemberBrandLevelMapper;
    private final UmsMemberLogMapper umsMemberLogMapper;
    private final UmsRechargeOrderMapper umsRechargeOrderMapper;
    private final UmsMemberQuantityRightMapper quantityRightMapper;
    private final UmsMemberAmountRightMapper amountRightMapper;
    private final UmsMemberTargetPlanMapper targetPlanMapper;
    private final MemberOrderExistenceQuery memberOrderExistenceQuery;

    // 🌟 注入双擎翻译服务
    private final SysDictDetailService sysDictDetailService;
    private final BrandNameQuery brandNameQuery;
    private final MemberCloudSyncOutboxService cloudSyncOutboxService;

    private static final String STATUS_UNUSED = "UNUSED";

    /**
     * 🌟 翻译引擎 1：加载全量品牌映射 (ID -> 名称)
     */
    private Map<String, String> getBrandMap(Collection<String> brandIds) {
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

    public PageVO<UmsMemberVO> list(UmsMemberQueryDTO queryDTO) {
        LambdaQueryWrapper<UmsMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StrUtil.isNotBlank(queryDTO.getCode()), UmsMember::getCode, queryDTO.getCode())
                .like(StrUtil.isNotBlank(queryDTO.getName()), UmsMember::getName, queryDTO.getName())
                .like(StrUtil.isNotBlank(queryDTO.getPhone()), UmsMember::getPhone, queryDTO.getPhone())
                .eq(StrUtil.isNotBlank(queryDTO.getType()), UmsMember::getType, queryDTO.getType())
                .eq(UmsMember::getDeleted, false);

        if (StrUtil.isNotBlank(queryDTO.getOrderBy())) {
            wrapper.last(queryDTO.getOrderBySql());
        } else {
            wrapper.orderByDesc(UmsMember::getUpdateTime);
        }

        Page<UmsMember> page = umsMemberMapper.selectPage(PageUtil.toPage(queryDTO), wrapper);
        PageVO<UmsMemberVO> pageVO = PageUtil.toPageVO(page, UmsMemberVO::new);

        if (pageVO.getRecords() != null && !pageVO.getRecords().isEmpty()) {
            List<Long> memberIds = pageVO.getRecords().stream().map(UmsMemberVO::getId).collect(Collectors.toList());

            QueryWrapper<PosMemberCoupon> countQw = new QueryWrapper<>();
            countQw.select("member_id", "COUNT(id) as count")
                    .in("member_id", memberIds)
                    .eq("status", STATUS_UNUSED)
                    .groupBy("member_id");
            List<Map<String, Object>> counts = posMemberCouponMapper.selectMaps(countQw);
            Map<Long, Integer> voucherCountMap = counts.stream().collect(Collectors.toMap(
                    m -> ((Number) m.get("member_id")).longValue(),
                    m -> ((Number) m.get("count")).intValue()
            ));

            List<UmsMemberBrandLevel> allBrandLevels = umsMemberBrandLevelMapper.selectList(
                    new LambdaQueryWrapper<UmsMemberBrandLevel>().in(UmsMemberBrandLevel::getMemberId, memberIds)
            );
            Map<Long, List<UmsMemberBrandLevel>> blMap = allBrandLevels.stream().collect(Collectors.groupingBy(UmsMemberBrandLevel::getMemberId));

            // 🌟 核心防爆破：一次性加载双擎缓存，避免在 for 循环中查库
            Map<String, String> brandMap = getBrandMap(allBrandLevels.stream()
                    .map(UmsMemberBrandLevel::getBrand).collect(Collectors.toSet()));
            Map<String, String> levelDictMap = getMemberLevelDictMap();

            for (UmsMemberVO vo : pageVO.getRecords()) {
                vo.setVoucherCount(voucherCountMap.getOrDefault(vo.getId(), 0));
                List<UmsMemberBrandLevel> levels = blMap.get(vo.getId());

                Map<String, String> levelMap = new HashMap<>();      // 存旧数据 (防前端报错)
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
            }
        }
        return pageVO;
    }

    // ... (add, update, getTop20Goods, getDormantMembers, delete, saveBrandLevels 方法保持完全不动) ...

    public void add(UmsMemberDTO addDTO) {
        UmsMember umsMember = new UmsMember();
        BeanUtil.copyProperties(addDTO, umsMember);
        String newCode;
        do {
            newCode = RandomUtil.randomNumbers(8);
        } while (umsMemberMapper.exists(new LambdaQueryWrapper<UmsMember>().eq(UmsMember::getCode, newCode)));

        umsMember.setCode(newCode);
        umsMember.setBalance(BigDecimal.ZERO);
        umsMember.setCoupon(BigDecimal.ZERO);
        umsMemberMapper.insert(umsMember);

        saveBrandLevels(umsMember.getId(), addDTO.getBrandLevels());
        cloudSyncOutboxService.markUpsert(umsMember.getId());
    }

    public void update(UmsMemberDTO updateDTO) {
        UmsMember umsMember = umsMemberMapper.selectById(updateDTO.getId());
        if (umsMember == null) {
            throw new BaseException(BizErrorStatus.MEMBER_NOT_FOUND, "会员不存在或已被删除");
        }

        BeanUtil.copyProperties(updateDTO, umsMember);
        umsMemberMapper.updateById(umsMember);

        saveBrandLevels(umsMember.getId(), updateDTO.getBrandLevels());
        cloudSyncOutboxService.markUpsert(umsMember.getId());
    }

    public List<MemberGoodsRankVO> getTop20Goods(Long memberId) {
        List<Map<String, Object>> rankData = umsMemberMapper.getTop20Goods(memberId);
        if (rankData == null || rankData.isEmpty()) {
            return new ArrayList<>();
        }
        return rankData.stream()
                .map(map -> new MemberGoodsRankVO(
                        (String) map.get("goodsName"),
                        ((Number) map.get("buyCount")).intValue()))
                .collect(Collectors.toList());
    }

    public List<UmsMemberVO> getDormantMembers(Integer days) {
        if (days == null || days <= 0) throw new BaseException("天数参数异常");
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        List<UmsMember> list = umsMemberMapper.selectList(new LambdaQueryWrapper<UmsMember>()
                .eq(UmsMember::getDeleted, false)
                .and(wrapper -> wrapper
                        .and(w1 -> w1.isNotNull(UmsMember::getLastVisitTime).le(UmsMember::getLastVisitTime, threshold))
                        .or(w2 -> w2.isNull(UmsMember::getLastVisitTime).le(UmsMember::getCreateTime, threshold))
                )
                .orderByDesc(UmsMember::getConsumeAmount)
                .last("LIMIT 100"));
        return BeanUtil.copyToList(list, UmsMemberVO.class);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        List<UmsMember> membersToDelete = umsMemberMapper.selectBatchIds(ids);
        if (membersToDelete == null || membersToDelete.isEmpty()) return;
        for (UmsMember member : membersToDelete) {
            requireEmptyErroneousProfile(member);
        }
        for (UmsMember member : membersToDelete) {
            UmsMember updateEntity = new UmsMember();
            updateEntity.setId(member.getId());
            updateEntity.setDeleted(true);
            umsMemberMapper.updateById(updateEntity);
            cloudSyncOutboxService.markTombstone(member.getId());
        }
    }

    private void requireEmptyErroneousProfile(UmsMember member) {
        Long memberId = member.getId();
        if (positive(member.getBalance()) || positive(member.getCoupon())) {
            rejectDeletion(memberId, "仍有余额或券额度");
        }
        if (umsRechargeOrderMapper.exists(new LambdaQueryWrapper<UmsRechargeOrder>()
                .eq(UmsRechargeOrder::getMemberId, memberId))
                || umsMemberLogMapper.exists(new LambdaQueryWrapper<UmsMemberLog>()
                .eq(UmsMemberLog::getMemberId, memberId))) {
            rejectDeletion(memberId, "存在充值或资产流水");
        }
        if (posMemberCouponMapper.exists(new LambdaQueryWrapper<PosMemberCoupon>()
                .eq(PosMemberCoupon::getMemberId, memberId))) {
            rejectDeletion(memberId, "存在会员券记录");
        }
        if (umsMemberBrandLevelMapper.exists(new LambdaQueryWrapper<UmsMemberBrandLevel>()
                .eq(UmsMemberBrandLevel::getMemberId, memberId))) {
            rejectDeletion(memberId, "存在品牌等级记录");
        }
        if (quantityRightMapper.exists(new LambdaQueryWrapper<UmsMemberQuantityRight>()
                .eq(UmsMemberQuantityRight::getMemberId, memberId))
                || amountRightMapper.exists(new LambdaQueryWrapper<UmsMemberAmountRight>()
                .eq(UmsMemberAmountRight::getMemberId, memberId))
                || targetPlanMapper.exists(new LambdaQueryWrapper<UmsMemberTargetPlan>()
                .eq(UmsMemberTargetPlan::getMemberId, memberId))) {
            rejectDeletion(memberId, "存在会员权益或目标计划");
        }
        if (memberOrderExistenceQuery.hasOrderForMemberId(memberId)) {
            rejectDeletion(memberId, "存在订单记录");
        }
    }

    private boolean positive(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) != 0;
    }

    private void rejectDeletion(Long memberId, String reason) {
        throw new BaseException("会员“" + memberId + "”不能删除：" + reason + "；请保留档案或使用会员合并");
    }

    public void saveBrandLevels(Long memberId, Map<String, String> brandLevels) {
        umsMemberBrandLevelMapper.delete(new LambdaQueryWrapper<UmsMemberBrandLevel>().eq(UmsMemberBrandLevel::getMemberId, memberId));
        if (brandLevels != null) {
            brandLevels.forEach((b, level) -> {
                if (StrUtil.isNotBlank(level)) {
                    UmsMemberBrandLevel bl = new UmsMemberBrandLevel();
                    bl.setMemberId(memberId);
                    bl.setBrand(b);
                    bl.setLevelCode(level);
                    umsMemberBrandLevelMapper.insert(bl);
                }
            });
        }
    }

    public UmsMemberVO getDetail(Long id) {
        UmsMember member = umsMemberMapper.selectById(id);
        if (member == null) {
            throw new BaseException(BizErrorStatus.MEMBER_NOT_FOUND, "会员不存在");
        }

        UmsMemberVO vo = new UmsMemberVO();
        cn.hutool.core.bean.BeanUtil.copyProperties(member, vo);

        Long vCount = posMemberCouponMapper.selectCount(
                new LambdaQueryWrapper<PosMemberCoupon>()
                        .eq(PosMemberCoupon::getMemberId, id)
                        .eq(PosMemberCoupon::getStatus, STATUS_UNUSED)
        );
        vo.setVoucherCount(vCount != null ? vCount.intValue() : 0);

        List<UmsMemberBrandLevel> levels = umsMemberBrandLevelMapper.selectList(
                new LambdaQueryWrapper<UmsMemberBrandLevel>().eq(UmsMemberBrandLevel::getMemberId, id)
        );

        Map<String, String> levelMap = new HashMap<>();
        Map<String, String> levelDescMap = new HashMap<>();

        if (levels != null && !levels.isEmpty()) {
            // 🌟 详情页依然执行双擎翻译
            Map<String, String> brandMap = getBrandMap(levels.stream()
                    .map(UmsMemberBrandLevel::getBrand).collect(Collectors.toSet()));
            Map<String, String> levelDictMap = getMemberLevelDictMap();

            for (UmsMemberBrandLevel bl : levels) {
                levelMap.put(bl.getBrand(), bl.getLevelCode());

                String brandName = brandMap.getOrDefault(bl.getBrand(), "未知品牌(" + bl.getBrand() + ")");
                String safeLevelCode = bl.getLevelCode() != null ? bl.getLevelCode().trim().toUpperCase() : "";
                String levelName = levelDictMap.getOrDefault(safeLevelCode, bl.getLevelCode());

                levelDescMap.put(brandName, levelName);
            }
        }
        vo.setBrandLevels(levelMap);
        vo.setBrandLevelDesc(levelDescMap); // 🌟 挂载纯中文语义矩阵

        return vo;
    }
}
