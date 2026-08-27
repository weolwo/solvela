package solvela.lottery.prizerule.dao;

        import java.util.List;

        import solvela.lottery.LotteryPrizeRule;
        import solvela.lottery.prizerule.domain.query.LotteryPrizeRuleQuery;
        import solvela.lottery.prizerule.domain.dto.LotteryPrizeRuleDTO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 彩票奖励配置 Dao
 *
 * @Author weolwo
 * @Date 2026-04-19 11:50:34
 * @Copyright weolwo
 */
@Mapper
public interface LotteryPrizeRuleDao extends BaseMapper<LotteryPrizeRule> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<LotteryPrizeRuleDTO> queryPage(Page<?> page, @Param("queryForm") LotteryPrizeRuleQuery queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<LotteryPrizeRuleDTO> queryList(@Param("queryForm") LotteryPrizeRuleQuery queryForm);

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