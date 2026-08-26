package solvela.ledger.coupon.dao;

import java.util.List;

import solvela.ledger.MemberCoupon;
import solvela.ledger.coupon.domain.dto.MemberCouponDTO;
import solvela.ledger.coupon.domain.query.MemberCouponQuery;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import solvela.ledger.stat.domain.form.LedgerStatForm;

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

    /*
     * 原先这里有 deleteById / batchDelete 两个<b>物理删除</b>，已随写接口一起移除（v3.69.0）。
     * 账务与审计流水删掉就再也查不回来，事后连"少了什么"都不知道。
     */
}