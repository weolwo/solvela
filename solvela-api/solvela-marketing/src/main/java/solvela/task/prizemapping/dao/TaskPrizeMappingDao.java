package solvela.task.prizemapping.dao;

        import java.util.List;

        import solvela.task.prizemapping.domain.entity.TaskPrizeMapping;
        import solvela.task.prizemapping.domain.form.TaskPrizeMappingQueryForm;
        import solvela.task.prizemapping.domain.vo.TaskPrizeMappingVO;
        import solvela.base.common.dao.CustomizedBaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 任务阶段与奖励映射表 Dao
 *
 * <p>只读：这张表的写入口只有任务向导（{@code TaskConfigService.wizardSubmit/wizardUpdate}，
 * 整体删掉重插）。管理端不再提供单条增删改 —— 详见 Controller 上的说明。
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

}
