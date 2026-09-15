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
     * 本期核销<b>金额</b>合计。
     *
     * <p>管理端的券统计一直有个「本期核销」口径，但在本表出现之前它只数得出
     * <b>张数</b> —— 实际减了多少没人记，所以金额算不出来。这个方法补的就是那一半。
     *
     * <h3>⚠️ 时间窗在 SQL 里算，不在 Java 里算</h3>
     * 和 {@code MemberCouponDao} 那几个统计口径共用同一套约定：两个日期都为 null 时
     * 用数据库的 {@code CURDATE()} 落到当天（铁律 9：时间只认数据库一个钟）。
     * 在 Java 里算 {@code LocalDate.now()} 会引入第二个时钟源。
     *
     * <h3>⚠️ 它和「本期核销张数」可能对不齐，而那不是 bug</h3>
     * 张数走的是 {@code t_member_coupon.used_time}，金额走的是本表 CONFIRM 行的
     * {@code create_time}。两者在同一次核销里是同一时刻，但<b>2026-09-15 三阶段核销
     * 上线之前用掉的券没有流水行</b> —— 那批券有 used_time、没有金额。
     * 所以历史区间上会出现「张数 &gt; 0 而金额为 0」，那是真实的历史，不是漏算。
     *
     * @return 没有任何核销时返回 null，调用方按 0 处理
     */
    BigDecimal sumConfirmedAmount(@Param("form") solvela.ledger.stat.domain.query.LedgerStatQuery form);

    /**
     * 这一笔对这张券的<b>锁定</b>那一行。
     *
     * <h3>为什么确认/释放要回头读它</h3>
     * 流水表的 {@code original_amount} 是 NOT NULL —— 但确认和释放的调用方
     * 未必知道订单金额（超时取消那条路拿到的只有订单号）。让它们都带着金额
     * 传进来，就等于要求每个调用方都记住一份本该由券自己记住的东西，
     * 而漏传的表现是<b>流水里的抵扣前金额是个编出来的数</b>，对账时才发现。
     *
     * <p>锁定那一行本来就有这两个金额，回头抄一份是最短也最诚实的路径。
     *
     * @return 没有对应锁定行时返回 null —— 那说明调用方在确认一笔它没锁过的券
     */
    CouponWriteOff selectLockRow(@Param("couponId") Long couponId, @Param("bizRefId") String bizRefId);
}
