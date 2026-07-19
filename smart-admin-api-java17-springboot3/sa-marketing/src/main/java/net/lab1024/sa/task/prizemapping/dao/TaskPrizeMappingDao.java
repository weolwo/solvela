package net.lab1024.sa.task.prizemapping.dao;

        import java.util.List;
        import net.lab1024.sa.task.prizemapping.domain.entity.TaskPrizeMapping;
        import net.lab1024.sa.task.prizemapping.domain.form.TaskPrizeMappingQueryForm;
        import net.lab1024.sa.task.prizemapping.domain.vo.TaskPrizeMappingVO;
import net.lab1024.sa.base.common.dao.CustomizedBaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 任务阶段与奖励映射表 Dao
 *
 * @Author weolwo
 * @Date 2026-04-18 20:41:02
 * @Copyright weolwo
 */
@Mapper
public interface TaskPrizeMappingDao extends CustomizedBaseMapper<TaskPrizeMapping> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<TaskPrizeMappingVO> queryPage(Page<?> page, @Param("queryForm") TaskPrizeMappingQueryForm queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<TaskPrizeMappingVO> queryList(@Param("queryForm") TaskPrizeMappingQueryForm queryForm);

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