package com.money.feature.ums.application.member;

import com.money.entity.UmsMember;
import com.baomidou.mybatisplus.extension.service.IService;
import com.money.web.vo.PageVO;
import com.money.dto.UmsMember.UmsMemberDTO;
import com.money.dto.UmsMember.UmsMemberQueryDTO;
import com.money.dto.UmsMember.UmsMemberVO;
import com.money.dto.UmsMember.MemberRankVO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 会员表 服务类 (金融级防弹重构版)
 * </p>
 *
 * @author money
 * @since 2023-02-27
 */
public interface UmsMemberService extends IService<UmsMember> {

    PageVO<UmsMemberVO> list(UmsMemberQueryDTO queryDTO);

    void add(UmsMemberDTO addDTO);

    void update(UmsMemberDTO updateDTO);

    void delete(Set<Long> ids);

    /**
     * 🌟 核心重构1：收口会员消费与单品券的原子扣减
     *
     * @param id           会员id
     * @param amount       消费总额 (累加消费记录用)
     * @param couponAmount 本次核销的单品会员券总额
     * @param orderNo      关联订单号 (写日志溯源用)
     */
    void consume(Long id, BigDecimal amount, BigDecimal couponAmount, String orderNo);

    /**
     * 🌟 核心重构2：新增统一的会员余额原子扣减网关 (带 CAS 防超扣与独立日志)
     *
     * @param memberId 会员ID
     * @param amount   扣除金额
     * @param orderNo  关联订单号
     * @param remark   变动备注
     */
    void deductBalance(Long memberId, BigDecimal amount, String orderNo, String remark);

    /**
     * 售后退回
     *
     * @param id                  id
     * @param amount              消费金额
     * @param coupon              优惠券
     * @param increaseCancelTimes 增加退单次数
     * @param orderNo             关联订单号
     */
    void processReturn(Long id, BigDecimal amount, BigDecimal coupon, boolean increaseCancelTimes, String orderNo);

    void recharge(com.money.dto.Ums.RechargeDTO dto);

    /**
     * 老会员 Excel 批量导入
     */
    String importMembers(org.springframework.web.multipart.MultipartFile file);

    // ==========================================
    // 营销画像辅助接口
    // ==========================================
    /**
     * 获取会员最爱购买的 Top 20 商品
     */
    java.util.List<UmsMemberServiceImpl.MemberGoodsRankVO> getTop20Goods(Long memberId);

    /**
     * 沉睡雷达：按天数筛选流失会员
     */
    java.util.List<UmsMemberVO> getDormantMembers(Integer days);

    /**
     * 导弹发射：批量为指定会员派发满减券
     */
    void batchIssueVoucher(java.util.List<Long> memberIds, Long ruleId, Integer quantity);

    /**
     * 充值订单红冲/撤销
     * @param orderNo 充值单号
     * @param reason 撤销原因
     */
    void voidRecharge(String orderNo, String reason);

    /**
     * 获取单条会员完整画像详情 (包含余额、券、品牌特权等)
     */
    UmsMemberVO getDetail(Long id);

    List<MemberRankVO> getTopConsumeMembers();

    List<MemberRankVO> getTopBalanceMembers();

    List<MemberRankVO> getTopFrequencyMembers();
}
