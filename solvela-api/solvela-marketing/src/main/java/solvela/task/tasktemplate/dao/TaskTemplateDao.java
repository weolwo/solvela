package solvela.task.tasktemplate.dao;

        import java.util.List;

        import solvela.task.TaskTemplate;
        import solvela.task.tasktemplate.domain.query.TaskTemplateQuery;
        import solvela.task.tasktemplate.domain.dto.TaskTemplateDTO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 任务模板表 Dao
 *
 * @Author weolwo
 * @Date 2026-04-18 21:12:49
 * @Copyright weolwo
 */
@Mapper
public interface TaskTemplateDao extends BaseMapper<TaskTemplate> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<TaskTemplateDTO> queryPage(Page<?> page, @Param("queryForm") TaskTemplateQuery queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<TaskTemplateDTO> queryList(@Param("queryForm") TaskTemplateQuery queryForm);

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