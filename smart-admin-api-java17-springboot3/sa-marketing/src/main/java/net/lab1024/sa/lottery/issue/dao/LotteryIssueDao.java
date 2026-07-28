package net.lab1024.sa.lottery.issue.dao;

        import java.util.List;
        import net.lab1024.sa.lottery.issue.domain.entity.LotteryIssue;
        import net.lab1024.sa.lottery.issue.domain.form.LotteryIssueQueryForm;
        import net.lab1024.sa.lottery.issue.domain.vo.LotteryIssueVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 期号配置 Dao
 *
 * @Author weolwo
 * @Date 2026-05-09 16:54:51
 * @Copyright weolwo
 */
@Mapper
public interface LotteryIssueDao extends BaseMapper<LotteryIssue> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<LotteryIssueVO> queryPage(Page<?> page, @Param("queryForm") LotteryIssueQueryForm queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<LotteryIssueVO> queryList(@Param("queryForm") LotteryIssueQueryForm queryForm);

    /**
     * 数据库当前时间。
     *
     * 售卖窗口判定必须用它，不能用 JVM 的 LocalDateTime.now() —— 铁律 9/10：
     * 全系统只认数据库一个时钟。多实例部署时各节点 JVM 时钟未必一致，
     * 用 JVM 时间会出现「A 节点认为还能买、B 节点认为已截止」。
     */
    java.time.LocalDateTime selectDbNow();

    /**
     * 已发数 +1，条件更新做防超发兜底。
     *
     * WHERE sold_count < total_count 让 DB 成为最后一道闸门：
     * 即便 Redis 游标判定被绕过（比如误清了 Redis），这里也不会让已发数超过上限。
     *
     * @return 影响行数，0 表示已达上限
     */
    int increaseSoldCount(@Param("id") Long id);


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