package solvela.external.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import solvela.external.ExternalOrder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 外部场景消费单 Dao。
 *
 * <h3>🔴 状态流转全是【条件更新】，条件就是闸门本身</h3>
 * 每一条 {@code UPDATE} 的 {@code WHERE} 里都带着<b>前一个状态</b>。
 * 那不是防御性写法：先查再改的话，支付和超时取消会同时看到「待支付」，
 * 然后一个把单子付了、一个把券放回去 —— 而两边都以为自己成功了。
 *
 * <p>🔴 {@code update_time} 一律不出现在 SET 里：那一列是
 * {@code ON UPDATE CURRENT_TIMESTAMP}，数据库自己会填（铁律 9：时间只认数据库时钟）。
 *
 * <p>⚠️ 状态值这里是硬编码的数字，因为它们要进 SQL。改
 * {@code ExternalOrderStatusEnum} 的取值时这几条要一起改。
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
@Mapper
public interface ExternalOrderDao extends BaseMapper<ExternalOrder> {

    /** 按单号取单。order_no 上有唯一键，一定是 0 或 1 行 */
    @Select("SELECT * FROM t_external_order WHERE order_no = #{orderNo}")
    ExternalOrder getByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 待支付(0) → 待充值(10)。<b>支付的幂等闸门。</b>
     *
     * @return 1 表示本次成功付掉，0 表示已被别人改过（多半是超时 job 先到）
     */
    @Update("""
            UPDATE t_external_order
               SET status = 10, pay_time = NOW()
             WHERE order_no = #{orderNo} AND status = 0
            """)
    int markPaid(@Param("orderNo") String orderNo);

    /**
     * 待充值(10) → 充值中(20)。<b>调外部接口的幂等闸门。</b>
     *
     * <p>抢到它的那一个才去调运营商。没有这道闸的话，重试和定时补偿会
     * 同时调两次 —— 而那是<b>给用户充了两次话费</b>，外部接口那一侧没有回头路。
     */
    @Update("""
            UPDATE t_external_order
               SET status = 20
             WHERE order_no = #{orderNo} AND status = 10
            """)
    int markExecuting(@Param("orderNo") String orderNo);

    /** 充值中(20) → 成功(30)，回填外部流水号（对账靠它） */
    @Update("""
            UPDATE t_external_order
               SET status = 30, external_ref_no = #{externalRefNo}, finish_time = NOW()
             WHERE order_no = #{orderNo} AND status = 20
            """)
    int markSuccess(@Param("orderNo") String orderNo, @Param("externalRefNo") String externalRefNo);

    /**
     * 充值中(20) → 失败(60)。
     *
     * <p>🔴 <b>失败不放券</b>，和商城履约失败同一条规矩：东西还欠着用户，不是没买。
     * 放了券却没退钱，用户会拿到一个自相矛盾的结果。
     */
    @Update("""
            UPDATE t_external_order
               SET status = 60, fail_reason = #{failReason}, finish_time = NOW()
             WHERE order_no = #{orderNo} AND status = 20
            """)
    int markFailed(@Param("orderNo") String orderNo, @Param("failReason") String failReason);

    /**
     * 待支付(0) → 已取消(40)。<b>超时 job 唯一的闸门。</b>
     *
     * <p>拿到 0 行就必须<b>整单放弃补偿</b> —— 否则会把一个刚支付成功的单子的券放回去。
     */
    @Update("""
            UPDATE t_external_order
               SET status = 40, fail_reason = #{reason}
             WHERE order_no = #{orderNo} AND status = 0
            """)
    int markCancelled(@Param("orderNo") String orderNo, @Param("reason") String reason);

    /** 捞出已经超时的待支付单。按 expire_time 升序：最早过期的先处理 */
    @Select("""
            SELECT * FROM t_external_order
             WHERE status = 0 AND expire_time IS NOT NULL AND expire_time < #{now}
             ORDER BY expire_time
             LIMIT #{limit}
            """)
    List<ExternalOrder> selectExpiredUnpaid(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** 我的外部单，新的在前 */
    @Select("""
            SELECT * FROM t_external_order
             WHERE member_id = #{memberId}
             ORDER BY id DESC
             LIMIT #{limit}
            """)
    List<ExternalOrder> selectMyOrders(@Param("memberId") Long memberId, @Param("limit") int limit);
}
