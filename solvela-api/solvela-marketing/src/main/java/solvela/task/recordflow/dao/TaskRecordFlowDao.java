package solvela.task.recordflow.dao;

import solvela.task.recordflow.domain.entity.TaskRecordFlow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务事件流水 Dao
 *
 * @Author alaric
 * @Date 2026-08-01
 */
@Mapper
public interface TaskRecordFlowDao extends BaseMapper<TaskRecordFlow> {

    /**
     * 按任务记录查流水（客诉复盘用，按 id 正序还原推进过程）。
     *
     * <p>流水必须<b>先于</b>进度推进落库（幂等键要先占住），此时记录可能还没建，
     * 故 record_id 允许后补 —— 补写走 {@code updateById}，与终态字段
     * （flow_type / delta_metric / after_metric / discard_reason）一次性写回。
     */
    List<TaskRecordFlow> selectByRecordId(@Param("recordId") Long recordId);
}
