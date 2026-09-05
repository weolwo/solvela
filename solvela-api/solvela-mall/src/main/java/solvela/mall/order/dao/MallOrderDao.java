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

    /** 按订单号取单。order_no 上有唯一键，一定是 0 或 1 行 */
    @Select("SELECT * FROM t_mall_order WHERE order_no = #{orderNo}")
    MallOrder getByOrderNo(@Param("orderNo") String orderNo);
}