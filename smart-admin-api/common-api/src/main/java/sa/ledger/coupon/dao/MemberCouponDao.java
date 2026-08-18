package sa.ledger.coupon.dao;

        import java.util.List;
        import sa.ledger.coupon.domain.entity.MemberCoupon;
        import sa.ledger.coupon.domain.form.MemberCouponQueryForm;
        import sa.ledger.coupon.domain.vo.MemberCouponVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import sa.ledger.stat.domain.form.LedgerStatForm;
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
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MemberCouponVO> queryPage(Page<?> page, @Param("queryForm") MemberCouponQueryForm queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MemberCouponVO> queryList(@Param("queryForm") MemberCouponQueryForm queryForm);

    // ==================== 统计面板 ====================

    /**
     * 本期发放（时间窗落在 {@code create_time}）：张数、会员数、这批券里已用掉的张数。
     */
    java.util.Map<String, Object> selectIssuedStat(@Param("form") LedgerStatForm form);

    /**
     * 本期核销（时间窗落在 {@code used_time}）。
     *
     * <p>⚠️ 与发放<b>不是同一批券</b> —— 今天核销的券可能是上个月发的，两个数不要相减。
     * 用同一个窗口算「今日核销 / 今日发放」会得到一个必然接近 0 的比率，
     * 而它错得很像一条正常的业务结论。
     */
    java.util.Map<String, Object> selectUsedStat(@Param("form") LedgerStatForm form);

    /**
     * 券库存与一致性体检。<b>刻意不带时间窗，统计的是全量</b>：
     * 「手上还压着多少张没用的券」是存量问题，限制在今天只会把它藏起来。
     */
    java.util.Map<String, Object> selectStockStat();

    /**
     * 券模维度分布（本期发放量 TOP 10）
     */
    List<java.util.Map<String, Object>> selectCouponStat(@Param("form") LedgerStatForm form);

    /**
     * 来源维度分布（本期发放）
     */
    List<java.util.Map<String, Object>> selectSourceStat(@Param("form") LedgerStatForm form);

            // ----- 物理删除 -----
                /**
                 * 单个物理删除
                 */
                long deleteById(@Param("id") Long id);

                /**
                 * 批量物理删除
                 */
                void batchDelete(@Param("idList") List<Long> idList);
}