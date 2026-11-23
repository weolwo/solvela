package solvela.task.taskconfig.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import solvela.task.TaskConfig;
import solvela.task.taskconfig.domain.dto.TaskConfigDTO;
import solvela.task.taskconfig.domain.query.TaskConfigQuery;

import java.util.List;

/**
 * 任务配置表 Dao
 *
 * @Author weolwo
 * @Date 2026-04-18 20:55:10
 * @Copyright weolwo
 */
@Mapper
public interface TaskConfigDao extends BaseMapper<TaskConfig> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<TaskConfigDTO> queryPage(Page<?> page, @Param("queryForm") TaskConfigQuery queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<TaskConfigDTO> queryList(@Param("queryForm") TaskConfigQuery queryForm);

            // ----- 物理删除 -----
                /**
                 * 单个物理删除
                 */
                long deleteById(@Param("id") Long id);

                /**
                 * 批量物理删除
                 */
                void batchDelete(@Param("idList") List<Long> idList);

    /*
     * 🔴 上 / 下线闸门，带旧状态条件，返回影响行数：0 表示已不在前置状态。
     * 迁移见 TaskConfigStatusEnum#canTransitTo，TaskConfigStatusSqlTest 断言一一对应。
     */

    /** 重新上线：已下线 → 待生效（运行态的判据是「不是已下线」，待生效就是可用） */
    int markPending(@Param("id") Long id);

    /** 下线：待生效 / 生效中 → 已下线 */
    int markOffline(@Param("id") Long id);
}
