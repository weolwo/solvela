package net.lab1024.sa.risk.promotionconfig.dao;

        import java.util.List;
        import net.lab1024.sa.risk.promotionconfig.domain.entity.PromotionConfig;
        import net.lab1024.sa.risk.promotionconfig.domain.form.PromotionConfigQueryForm;
        import net.lab1024.sa.risk.promotionconfig.domain.vo.PromotionConfigVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 优惠配置表 Dao
 *
 * @Author weolwo
 * @Date 2026-04-18 23:28:25
 * @Copyright weolwo
 */
@Mapper
public interface PromotionConfigDao extends BaseMapper<PromotionConfig> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<PromotionConfigVO> queryPage(Page<?> page, @Param("queryForm") PromotionConfigQueryForm queryForm);

    /**
     * 预算/数量原子预扣：把「够不够」的判断压进 UPDATE 的 WHERE 里，一条 SQL 完成校验+扣减
     *
     * 这才是真正的硬限流。风控链上的 GlobalBudgetRiskFilter 只是「先读后判」的弱校验，
     * 高并发下读到的余量早就过期了，多个请求会同时判定通过 —— 必须由这里的条件更新兜底。
     * total_amount / total_quota 为 -1 表示不限制，对应条件直接放行。
     *
     * @return 更新行数，0 表示预算或数量不足（此时没有任何字段被改动）
     */
    int deductBudget(@Param("id") Long id,
                     @Param("amount") BigDecimal amount,
                     @Param("quota") Integer quota);

    /**
     * 预扣回滚：资产下发失败时把占用的预算/数量还回去，否则预算会只减不加地泄漏
     * 用 CASE WHEN 兜住，保证不会被扣成负数
     */
    int releaseBudget(@Param("id") Long id,
                      @Param("amount") BigDecimal amount,
                      @Param("quota") Integer quota);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<PromotionConfigVO> queryList(@Param("queryForm") PromotionConfigQueryForm queryForm);

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