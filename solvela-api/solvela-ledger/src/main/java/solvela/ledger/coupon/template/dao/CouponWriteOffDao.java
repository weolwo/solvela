package solvela.ledger.coupon.template.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import solvela.coupon.CouponWriteOff;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 优惠券核销流水 Dao。<b>只增不改</b> —— 本接口刻意没有任何 update / delete。
 *
 * <p>流水改得动就不叫流水了。要更正一条错误的核销，正确做法是补一条
 * {@code RELEASE} 把它冲掉，而不是把原来那行改掉 —— 后者会让「发生过什么」
 * 这个问题永远失去答案。
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
@Mapper
public interface CouponWriteOffDao extends BaseMapper<CouponWriteOff> {

    /** 一张券的完整时间线，新的在前。客服拿着券号要回答「它经历了什么」 */
    List<CouponWriteOff> selectByCoupon(@Param("couponId") Long couponId);

    /**
     * 按单据号反查：这一单用了哪张券、减了多少。
     *
     * <p>🔴 <b>对账的入口</b>。走 {@code idx_biz}。
     */
    List<CouponWriteOff> selectByBiz(@Param("bizType") String bizType,
                                     @Param("bizRefId") String bizRefId);

    /**
     * 某段时间内的核销金额合计。
     *
     * <p>管理端现有的券统计里有个「本期核销」口径，在本表出现之前它<b>永远是 0</b>，
     * 而且就算券能用了也算不出<b>金额</b> —— 因为实际减了多少没人记。
     *
     * @return 没有任何核销时返回 null，调用方按 0 处理
     */
    BigDecimal sumConfirmedAmount(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
