package solvela.ledger.coupon.dao;

import java.util.List;

import solvela.ledger.MemberCoupon;
import solvela.ledger.coupon.domain.dto.MemberCouponDTO;
import solvela.ledger.coupon.domain.dto.MemberCouponExpiringDTO;
import solvela.ledger.coupon.domain.query.MemberCouponQuery;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import solvela.ledger.stat.domain.query.LedgerStatQuery;

import java.util.List;

/**
 * 会员优惠券 Dao
 *
 * @Author weolwo
 * @Date 2026-04-18 23:42:44
 * @Copyright weolwo
 */
@Mapper
public interface MemberCouponDao extends BaseMapper<MemberCoupon> {

    /**
     * 分页查询
     *
     * @param page      分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MemberCouponDTO> queryPage(Page<?> page, @Param("queryForm") MemberCouponQuery queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MemberCouponDTO> queryList(@Param("queryForm") MemberCouponQuery queryForm);

    // ==================== 统计面板 ====================

    /**
     * 本期发放（时间窗落在 {@code create_time}）：张数、会员数、这批券里已用掉的张数。
     */
    java.util.Map<String, Object> selectIssuedStat(@Param("form") LedgerStatQuery form);

    /**
     * 本期核销（时间窗落在 {@code used_time}）。
     *
     * <p>⚠️ 与发放<b>不是同一批券</b> —— 今天核销的券可能是上个月发的，两个数不要相减。
     * 用同一个窗口算「今日核销 / 今日发放」会得到一个必然接近 0 的比率，
     * 而它错得很像一条正常的业务结论。
     */
    java.util.Map<String, Object> selectUsedStat(@Param("form") LedgerStatQuery form);

    /**
     * 券库存与一致性体检。<b>刻意不带时间窗，统计的是全量</b>：
     * 「手上还压着多少张没用的券」是存量问题，限制在今天只会把它藏起来。
     */
    java.util.Map<String, Object> selectStockStat();

    /**
     * 券模维度分布（本期发放量 TOP 10）
     */
    List<java.util.Map<String, Object>> selectCouponStat(@Param("form") LedgerStatQuery form);

    /**
     * 来源维度分布（本期发放）
     */
    List<java.util.Map<String, Object>> selectSourceStat(@Param("form") LedgerStatQuery form);

    // ==================== 过期收口（couponExpire 定时任务） ====================

    /**
     * 已过有效期却仍是「未使用」的张数。给试运行用 —— 先看清楚要改多少行再动手。
     *
     * @param now 数据库时钟，由任务上下文传入（铁律 9：不要在这里用 JVM 时间）
     */
    long countExpirableCoupon(@Param("now") java.time.LocalDateTime now);

    /**
     * 把过了有效期的未使用券置为 2-已过期，一次最多 {@code limit} 行。
     *
     * <p>条件里带着 {@code status = 0}，所以天然幂等：重复执行第二遍影响行数就是 0，
     * 也不会把已使用/已作废的券误伤成过期。
     *
     * @return 实际更新行数
     */
    int expireCouponBatch(@Param("now") java.time.LocalDateTime now, @Param("limit") int limit);

    /**
     * 即将过期的券，<b>按会员聚合</b>：一个会员一行，带张数与最近一张的失效时间。
     *
     * <p>🔴 聚合在 SQL 里做，不是捞出全部券再在 Java 里 group —— 后者会把
     * 「N 张券」变成「N 行内存」，而这正是 {@code CouponExpiringNotifyJob} 要避免的：
     * 一个人 8 张券要发<b>一条</b>通知，不是 8 条。SQL 聚合让这件事在数据层就定了形，
     * 调用方想写错都难。
     *
     * @param from 窗口起点（通常是 now，排除已经过期的）
     * @param to   窗口终点（now + N 天）
     */
    List<MemberCouponExpiringDTO> selectExpiringGroupByMember(@Param("from") java.time.LocalDateTime from,
                                                              @Param("to") java.time.LocalDateTime to,
                                                              @Param("limit") int limit,
                                                              @Param("offset") int offset);

    // ==================== 三阶段核销（2026-09-15 阶段 3） ====================

    /**
     * 会员当前<b>未使用</b>的券，带规则快照。给试算用。
     *
     * <p>⚠️ 刻意<b>不</b>在 SQL 里过滤「门槛够不够 / 适用范围对不对」——
     * 试算要能回答「你手上这张券<b>为什么</b>用不了」。在 SQL 里筛掉，用户就只看到
     * 券凭空消失了，第一反应是系统坏了，而真实原因往往只是「没到门槛」。
     *
     * <p>过期券不在这里：它们由 {@code couponExpire} 任务收口成 2-已过期。
     *
     * @param now 数据库时钟。有效期判断只认一个钟（铁律 9）
     */
    List<MemberCoupon> selectUsableCandidates(@Param("memberId") Long memberId,
                                              @Param("now") java.time.LocalDateTime now);

    /**
     * 锁定：0-未使用 → 4-锁定中。
     *
     * <h3>🔴 条件更新本身就是并发闸</h3>
     * {@code WHERE status = 0} 让两笔订单抢同一张券时只有一笔能成 ——
     * 另一笔拿到 0 行，当场知道自己没抢到。先查再改的话，两笔都会看到「未使用」，
     * 然后都以为自己锁上了，最后一张券被两单用掉。
     *
     * <p>{@code member_id} 也在条件里：越权用别人的券这一条就挡住了，
     * 而且不需要先查一次再比对。
     *
     * @return 1=锁上了，0=没抢到（已被别人锁、已用掉、或不是这个人的券）
     */
    int lockCoupon(@Param("couponId") Long couponId,
                   @Param("memberId") Long memberId,
                   @Param("bizRefId") String bizRefId,
                   @Param("discountAmount") java.math.BigDecimal discountAmount,
                   @Param("now") java.time.LocalDateTime now);

    /**
     * 确认：4-锁定中 → 1-已使用。
     *
     * <p>条件里带着 {@code locked_biz_id = #{bizRefId}}：只有<b>锁它的那一笔</b>
     * 能确认它。少了这个条件，A 单锁的券会被 B 单确认掉。
     *
     * @return 1=确认了，0=它已经不是「被这一笔锁着」的状态了
     */
    int confirmCoupon(@Param("couponId") Long couponId,
                      @Param("bizRefId") String bizRefId,
                      @Param("now") java.time.LocalDateTime now);

    /**
     * 释放：4-锁定中 → 0-未使用，并清掉锁定痕迹与冗余的抵扣额。
     *
     * <p>同样只有锁它的那一笔能释放。
     *
     * @return 1=放回去了，0=它已经不是「被这一笔锁着」的状态了
     */
    int releaseCoupon(@Param("couponId") Long couponId, @Param("bizRefId") String bizRefId);

    /**
     * 卡在「锁定中」超过 {@code before} 的券。给兜底释放任务用。
     *
     * <p>⚠️ 这里<b>判不了对应单据是不是已经终态</b> —— 账务域不能依赖商城域
     *（有架构守卫测试盯着）。所以只能按时间兜底，阈值要比订单自己的支付超时
     * 宽得多，详见 {@code CouponStuckLockReleaseJob}。
     */
    List<MemberCoupon> selectStuckLocked(@Param("before") java.time.LocalDateTime before,
                                         @Param("limit") int limit);

    /*
     * 原先这里有 deleteById / batchDelete 两个<b>物理删除</b>，已随写接口一起移除（v3.69.0）。
     * 账务与审计流水删掉就再也查不回来，事后连"少了什么"都不知道。
     */
}