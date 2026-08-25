package solvela.task.taskevent.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import solvela.task.taskevent.domain.entity.TaskEvent;
import solvela.task.taskevent.domain.form.TaskEventQueryForm;
import solvela.task.taskevent.domain.vo.TaskEventVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务事件注册表 Dao
 *
 * @Author alaric
 * @Date 2026-08-01
 */
@Mapper
public interface TaskEventDao extends BaseMapper<TaskEvent> {

    List<TaskEventVO> queryPage(Page<?> page, @Param("queryForm") TaskEventQueryForm queryForm);

    /**
     * 按编码取（含停用的，由调用方判断状态 —— 停用与不存在要给不同的提示）
     */
    TaskEvent selectByEventCode(@Param("eventCode") String eventCode);

    /**
     * 启用中的事件列表，供向导下拉与运行态校验
     */
    List<TaskEvent> selectEnabledList();
}
