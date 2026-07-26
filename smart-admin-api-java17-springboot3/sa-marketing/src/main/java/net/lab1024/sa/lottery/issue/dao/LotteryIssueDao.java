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