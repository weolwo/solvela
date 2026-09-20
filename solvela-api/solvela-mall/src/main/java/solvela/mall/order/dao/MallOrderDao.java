package solvela.mall.order.dao;

        import java.util.List;

        import solvela.mall.MallOrder;
        import solvela.mall.order.domain.query.MallOrderQuery;
        import solvela.mall.order.domain.dto.MallOrderRankDTO;
        import solvela.mall.order.domain.dto.MallOrderStatDTO;
        import solvela.mall.order.domain.dto.MallOrderDTO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

/**
 * 商城-兑换订单 Dao
 *
 * @Author weolwo
 * @Date 2026-08-22 19:35:46
 * @Copyright weolwo
 */
@Mapper
public interface MallOrderDao extends BaseMapper<MallOrder> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MallOrderDTO> queryPage(Page<?> page, @Param("queryForm") MallOrderQuery queryForm);

    /**
     * 统计：一趟 SQL 出全部指标，条件与列表复用同一段 query_condition_items
     */
    MallOrderStatDTO queryStat(@Param("queryForm") MallOrderQuery queryForm);

    /**
     * 兑换商品排行（按兑换件数）
     */
    List<MallOrderRankDTO> queryCommodityRank(@Param("queryForm") MallOrderQuery queryForm, @Param("topN") int topN);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MallOrderDTO> queryList(@Param("queryForm") MallOrderQuery queryForm);

    /* ================= 履约状态机 =================
     *
     * 三个方法都是「条件 UPDATE」，都靠 affected rows 判成败，都不读后写。
     * 状态迁移写在 WHERE 里而不是先 select 再判断 —— 后者在并发下必然出现
     * 两个线程同时读到 10、同时认为自己该发货。
     *
     * 🔴 update_time 一律不出现在 SET 里：那一列是 ON UPDATE CURRENT_TIMESTAMP，
     * 数据库自己会填（铁律 9：时间只认数据库时钟）。
     *
     * ⚠️ 状态值这里是硬编码的数字，因为它们要进 SQL。改 MallOrderStatusEnum
     * 的取值时这几条要一起改 —— MallFulfillStateTest 会在改错时变红。
     */

    /**
     * 待履约(10) → 履约中(20)。<b>这就是履约的幂等闸门。</b>
     *
     * <p>返回 0 表示这单已经被别人抢走、或者压根不在待履约状态 ——
     * 两种情况的处置一样：什么都别做。
     *
     * <p>之所以幂等靠它而不靠下游的唯一键：{@code t_member_coupon} 上
     * <b>没有</b> {@code UNIQUE(source_type, source_biz_id)}，重复发券拦不住。
     * 实物和钱包那两条路各自有唯一键兜底，券这条只有这里。
     */
    @Update("UPDATE t_mall_order SET status = 20 WHERE order_no = #{orderNo} AND status = 10")
    int markFulfilling(@Param("orderNo") String orderNo);

    /** 履约中(20) → 已完成(30)，回填履约单引用 */
    @Update("""
            UPDATE t_mall_order
               SET status = 30, fulfill_ref_id = #{fulfillRefId}, finish_time = NOW()
             WHERE order_no = #{orderNo} AND status = 20
            """)
    int markFinished(@Param("orderNo") String orderNo, @Param("fulfillRefId") String fulfillRefId);

    /**
     * 履约中(20) → 履约失败(60)，留下原因。
     *
     * <p>🔴 <b>失败不退积分。</b>东西还欠着用户，不是没买 ——
     * 退了积分等于单方面取消订单，而运营可能只是漏配了券模，补上就能发。
     * 真要取消是另一条路（40-已取消 + {@code AssetDebitApi#refund}）。
     */
    @Update("""
            UPDATE t_mall_order
               SET status = 60, fail_reason = #{failReason}
             WHERE order_no = #{orderNo} AND status = 20
            """)
    int markFailed(@Param("orderNo") String orderNo, @Param("failReason") String failReason);

    /**
     * 待支付(0) → 待履约(10)，落支付时间。<b>支付的幂等闸门。</b>
     *
     * <p>🔴 {@code AND status = 0} 和 {@code markCancelled} 里那个是<b>同一把闸的两侧</b>：
     * 用户正在支付、超时 job 同时到点，两边都想改这一行。带上它之后只有一个能改成功。
     *
     * <p>谁赢都对，而且两边的后续动作都必须跟着这个结果走：
     * 支付赢了 → job 拿到 0 行，整单放弃补偿（否则会给一个刚支付成功的订单退积分）；
     * job 赢了 → 支付拿到 0 行，必须<b>原路退款</b>而不是假装成功
     *（今天是假支付，没有真钱要退；接了真网关之后这里就是退款的入口）。
     *
     * @return 1 表示本次成功付掉（可以继续转库存、确认券、投履约），0 表示已被别人改过
     */
    @Update("""
            UPDATE t_mall_order
               SET status = 10, pay_time = NOW()
             WHERE order_no = #{orderNo} AND status = 0
            """)
    int markPaid(@Param("orderNo") String orderNo);

    /**
     * 待支付(0) → 已取消(40)。<b>超时释放 job 唯一的闸门。</b>
     *
     * <p>🔴 {@code AND status = 0} 不是防御性写法，是<b>并发闸</b>：
     * 用户正在支付、job 同时到点，两边都想改这一行。带上它之后只有一个能改成功，
     * 另一个拿到 0 行 —— 而 job 拿到 0 行就<b>必须整单放弃补偿</b>，
     * 否则会给一个刚支付成功的订单退积分。
     *
     * @return 1 表示本次成功取消（可以继续退款/放库存），0 表示已被别人改过
     */
    @Update("""
            UPDATE t_mall_order
               SET status = 40, cancel_time = NOW(), fail_reason = #{reason}
             WHERE order_no = #{orderNo} AND status = 0
            """)
    int markCancelled(@Param("orderNo") String orderNo, @Param("reason") String reason);

    /**
     * 捞出已经超时的待支付单。
     *
     * <p>按 {@code expire_time} 升序：最早过期的先处理，避免一批堆积时
     * 新单反复插队、老单永远轮不上。
     */
    @Select("""
            SELECT * FROM t_mall_order
             WHERE status = 0 AND expire_time IS NOT NULL AND expire_time < #{now}
             ORDER BY expire_time
             LIMIT #{limit}
            """)
    List<MallOrder> selectExpiredUnpaid(@Param("now") java.time.LocalDateTime now, @Param("limit") int limit);

    /** 按订单号取单。order_no 上有唯一键，一定是 0 或 1 行 */
    @Select("SELECT * FROM t_mall_order WHERE order_no = #{orderNo}")
    MallOrder getByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 窗口内<b>已经扣款成功</b>的订单，供打点反查补推用（{@code MallOrderAuditProvider}）。
     *
     * <h3>🔴 判据 {@code status NOT IN (0, 40)} 和统计口径是<b>同一份</b></h3>
     * 它就是 {@code MallOrderMapper.xml} 里 {@code paid_statuses} 那个片段的字面复制：
     * 待支付(0) 与已取消(40) 不算，其余都算过款（已退款(50) 也算 ——
     * 它确实扣过，退款是另一笔账）。
     *
     * <p>另写一套判断是漂移的开始：宽了会给一批没付钱的单补出任务进度，
     * 窄了则补不全 —— 而"补不全"的表现和"根本没配这个任务"一模一样，不会有人发现。
     *
     * <p>按 {@code pay_time} 筛而不是 {@code create_time}：两条路的付款时刻差得很远 ——
     * 纯积分单落单即付款，混合单可能挂十几分钟才付。按创建时间筛会让后者
     * 在它真正付款的那个窗口里查不到。
     *
     * <p>正序返回：一批补推时先补早的，和 {@code selectExpiredUnpaid} 同一个理由。
     */
    @Select("""
            SELECT * FROM t_mall_order
             WHERE pay_time IS NOT NULL
               AND pay_time >= #{from} AND pay_time <= #{to}
               AND status NOT IN (0, 40)
             ORDER BY pay_time
             LIMIT #{limit}
            """)
    List<MallOrder> selectSettledBetween(@Param("from") java.time.LocalDateTime from,
                                         @Param("to") java.time.LocalDateTime to,
                                         @Param("limit") int limit);
}