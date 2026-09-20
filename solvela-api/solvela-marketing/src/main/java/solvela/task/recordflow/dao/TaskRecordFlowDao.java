package solvela.task.recordflow.dao;

import solvela.task.TaskRecordFlow;
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

    /**
     * 这批业务单号里，哪些<b>已经有过流水</b>（不管是计入还是被丢弃）。
     *
     * <p>给打点反查对账用：差集就是"漏掉的"。
     *
     * <h3>🔴 被丢弃的也算"处理过"</h3>
     * 不能只看 {@code flow_type} 是计入的那种。一条事件被判成"人群不匹配"「周期外」
     * 同样是<b>处理过</b>了 —— 把它当成漏掉再补推一次，结果还是同样的丢弃，
     * 只是每轮对账多写一条重复流水。幂等键会挡住入库，但那是在靠唯一键兜圈子。
     *
     * <h3>⚠️ 只要有【任意一个】任务处理过这个单号，就算已处理</h3>
     * 所以「昨天新建的任务」不会被倒灌历史订单 —— 那些单在别的任务下已经有流水了。
     * 这是刻意的：对账要补的是<b>投递丢失</b>，不是<b>追溯发奖</b>。
     * 真要给新任务倒灌历史数据，那是一次性的运营动作，不该由对账任务顺手做掉。
     */
    List<String> selectReportedBizIds(@Param("eventCode") String eventCode,
                                      @Param("bizIds") List<String> bizIds);
}
