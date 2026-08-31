package solvela.draw.drawlog.dao;

        import java.util.List;

        import solvela.draw.DrawPrizeLog;
        import solvela.draw.drawlog.domain.query.DrawPrizeLogQuery;
        import solvela.draw.drawlog.domain.dto.DrawPrizeLogDTO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 抽奖记录 Dao
 *
 * @Author weolwo
 * @Date 2026-04-19 09:21:26
 * @Copyright weolwo
 */
@Mapper
public interface DrawPrizeLogDao extends BaseMapper<DrawPrizeLog> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<DrawPrizeLogDTO> queryPage(Page<?> page, @Param("queryForm") DrawPrizeLogQuery queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<DrawPrizeLogDTO> queryList(@Param("queryForm") DrawPrizeLogQuery queryForm);

    /**
     * 漏斗计数：总数 / 中奖 / 未中奖 / 库存不足 / 异常 / 去重人数，一次扫表算完。
     *
     * 不拆成五条 count：五次执行不但慢，还可能落在不同数据快照上，
     * 出现「四个分项加起来不等于总数」这种自相矛盾。
     * 刻意不吃 status 条件 —— 它是漏斗要拆解的维度本身。
     */
    java.util.Map<String, Object> selectFunnel(@Param("queryForm") DrawPrizeLogQuery queryForm);

    /**
     * 奖品发放分布，按次数降序。只统计 status=1 的行 ——
     * 未中奖流水里的 prize_code 是「本来要给你的那个候选奖项」，不是真发出去的奖。
     */
    List<java.util.Map<String, Object>> selectPrizeHit(@Param("queryForm") DrawPrizeLogQuery queryForm);

            // ----- 物理删除 -----
                /**
                 * 单个物理删除
                 */
                long deleteById(@Param("id") Long id);

                /**
                 * 批量物理删除
                 */
                void batchDelete(@Param("idList") List<Long> idList);

    /**
     * 数据库当前时间。
     *
     * <p>算「本周期从哪一刻起」要据此定 {@code create_time} 的下界，不能用 JVM 时钟 ——
     * 铁律 9/10：全系统只认数据库一个时钟。多实例部署时跨零点那一刻，
     * A 节点认为还是昨天、B 节点认为已是今天，同一个用户能在两个周期里各抽一轮。
     */
    java.time.LocalDateTime selectDbNow();
}
