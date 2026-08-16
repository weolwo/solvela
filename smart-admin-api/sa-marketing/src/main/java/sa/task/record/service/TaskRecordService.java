package sa.task.record.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import sa.base.common.domain.PageResult;
import sa.base.common.domain.ResponseDTO;
import sa.base.common.util.SmartBeanUtil;
import sa.base.common.util.SmartCollectionUtil;
import sa.base.common.util.SmartPageUtil;
import sa.task.record.dao.TaskRecordDao;
import sa.task.record.domain.entity.TaskRecord;
import sa.task.record.domain.form.TaskRecordAddForm;
import sa.task.record.domain.form.TaskRecordQueryForm;
import sa.task.record.domain.form.TaskRecordStatusUpdateForm;
import sa.task.record.domain.form.TaskRecordUpdateForm;
import sa.task.record.domain.vo.TaskRecordFunnelVO;
import sa.task.record.domain.vo.TaskRecordVO;
import sa.task.constant.TaskDiscardCode;
import sa.task.taskconfig.domain.entity.TaskConfig;
import sa.task.taskconfig.manager.TaskConfigManager;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 任务记录表 Service
 *
 * @Author weolwo
 * @Date 2026-04-18 21:02:56
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class TaskRecordService {

    private final TaskRecordDao taskRecordDao;
    private final TaskConfigManager taskConfigManager;

    /**
     * 任务记录状态：3-已过期（对齐 TaskConst.RECORD_STATUS_EXPIRED）
     */
    private static final Integer STATUS_EXPIRED = 3;

    private static final int RATE_SCALE = 4;

    /**
     * 分页查询
     */
    public PageResult<TaskRecordVO> queryPage(TaskRecordQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<TaskRecordVO> list = taskRecordDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 任务记录漏斗。
     *
     * <p>回答的是翻十页记录也答不出来的问题：达标率多少、哪个任务没人做得完、
     * <b>用户明明做了事进度却为什么不涨</b>。最后一个只有流水表答得了 ——
     * 被丢弃的事件压根没建记录，在记录表里怎么翻都看不到。
     *
     * <p>另外盯住两个「不会有人主动查」的数字：已过有效期却仍在进行中的记录
     * （没有过期扫描任务，它们永远不会自己收口），以及停在「已完成」没流转到「已发奖」的记录
     * （达标闸门与发奖流转是紧邻的两条 SQL，停在中间说明发奖那步断了）。
     */
    public TaskRecordFunnelVO funnel(TaskRecordQueryForm queryForm) {
        Map<String, Object> row = taskRecordDao.selectFunnel(queryForm);
        TaskRecordFunnelVO vo = new TaskRecordFunnelVO();

        long total = toLong(row.get("totalCount"));
        long members = toLong(row.get("memberCount"));
        long running = toLong(row.get("runningCount"));
        long completed = toLong(row.get("completedCount"));
        long dispatched = toLong(row.get("dispatchedCount"));
        long expired = toLong(row.get("expiredCount"));
        long staleRunning = toLong(row.get("staleRunningCount"));

        vo.setTotalCount(total);
        vo.setMemberCount(members);
        vo.setRunningCount(running);
        vo.setCompletedCount(completed);
        vo.setDispatchedCount(dispatched);
        vo.setExpiredCount(expired);
        vo.setStaleRunningCount(staleRunning);
        /*
         * 达标率的分母用「接取总数」而不是「已收口的」：任务不像开奖有明确的揭晓时刻，
         * 进行中本身就是一种结果（做不完也是做不完），把它剔出分母会让达标率虚高。
         */
        vo.setReachRate(rate(completed + dispatched, total));
        vo.setRecordPerMember(members == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(total).divide(BigDecimal.valueOf(members), 2, RoundingMode.HALF_UP));

        // ---- 事件丢弃分布：进度不涨的原因就在这里 ----
        List<Map<String, Object>> discardStats = taskRecordDao.selectDiscardStat(queryForm);
        long discardTotal = discardStats.stream().mapToLong(s -> toLong(s.get("discardCount"))).sum();
        long discardAttention = 0L;
        List<TaskRecordFunnelVO.DiscardStatVO> discardList = new ArrayList<>();
        for (Map<String, Object> stat : discardStats) {
            TaskRecordFunnelVO.DiscardStatVO item = new TaskRecordFunnelVO.DiscardStatVO();
            String code = stat.get("discardCode") == null ? null : String.valueOf(stat.get("discardCode"));
            long count = toLong(stat.get("discardCount"));
            TaskDiscardCode discardCode = code == null ? null : TaskDiscardCode.resolve(code);
            item.setDiscardCode(code);
            // 归不了类的一律直说，不要编一个像模像样的名字盖住「写入侧没写 discard_code」这件事
            item.setDiscardDesc(discardCode == null ? "未归类（写入侧没写 discard_code）" : discardCode.getDesc());
            item.setDiscardCount(count);
            item.setDiscardShare(rate(count, discardTotal));
            item.setNeedsAttention(discardCode != null && discardCode.needsAttention());
            if (Boolean.TRUE.equals(item.getNeedsAttention())) {
                discardAttention += count;
            }
            discardList.add(item);
        }
        vo.setDiscardTotalCount(discardTotal);
        vo.setDiscardAttentionCount(discardAttention);
        vo.setDiscardList(discardList);

        // ---- 一致性 + 收口体检 ----
        List<String> issues = new ArrayList<>();
        if (staleRunning > 0) {
            issues.add("有 " + staleRunning + " 条记录已过有效期却仍是「进行中」：工程里没有过期扫描任务"
                    + "（idx_t_tsk_rec_expire 就是给它建的），这些记录不会自己收口，"
                    + "用户端会一直看到一个永远完不成的任务。可先用「批量禁用」置为已过期");
        }
        if (completed > 0) {
            issues.add("有 " + completed + " 条记录停在「已完成」没有流转到「已发奖」："
                    + "达标闸门与发奖流转是紧邻的两条 SQL，正常不会停在中间，"
                    + "出现说明发奖那一步断了。请到「发奖记录」确认这些人的奖到底发出去没有");
        }
        long reachedNoCompleteTime = toLong(row.get("reachedNoCompleteTime"));
        if (reachedNoCompleteTime > 0) {
            issues.add("有 " + reachedNoCompleteTime + " 条记录已达标却没有达标时间：客诉时说不清是哪天完成的，"
                    + "多半是人工改状态时没补 complete_time");
        }
        long runningWithCompleteTime = toLong(row.get("runningWithCompleteTime"));
        if (runningWithCompleteTime > 0) {
            issues.add("有 " + runningWithCompleteTime + " 条记录是「进行中」却带着达标时间：与状态自相矛盾，"
                    + "多半是被人工从已完成改回了进行中");
        }
        long reachedNoPrizeSnapshot = toLong(row.get("reachedNoPrizeSnapshot"));
        if (reachedNoPrizeSnapshot > 0) {
            issues.add("有 " + reachedNoPrizeSnapshot + " 条记录已达标但奖励快照是空的：接取时这个任务压根没配奖励，"
                    + "用户做完了也没有奖可发");
        }
        long noRuleSnapshot = toLong(row.get("noRuleSnapshot"));
        if (noRuleSnapshot > 0) {
            issues.add("有 " + noRuleSnapshot + " 条记录的规则快照为空：策略层读不到目标值，"
                    + "这些记录进度能涨但永远不会达标（任务配置的 rule_config 为空导致）");
        }
        if (discardAttention > 0) {
            issues.add("有 " + discardAttention + " 条事件因「上游未告知会员属性 / 任务配置异常 / 系统繁忙被拒」被丢弃："
                    + "这三类不是正常业务拦截，要去找上游补字段、修配置或扩容，光看丢弃总量会被正常拦截淹没");
        }
        vo.setIssueList(issues);

        // ---- 任务维度分布 ----
        List<Map<String, Object>> taskStats = taskRecordDao.selectTaskStat(queryForm);
        List<Long> configIds = taskStats.stream()
                .map(s -> s.get("taskConfigId"))
                .filter(Objects::nonNull)
                .map(v -> ((Number) v).longValue())
                .distinct()
                .toList();
        Map<Long, TaskConfig> configMap = configIds.isEmpty() ? Map.of()
                : taskConfigManager.lambdaQuery().in(TaskConfig::getId, configIds).list().stream()
                        .collect(Collectors.toMap(TaskConfig::getId, Function.identity(), (a, b) -> a));

        List<TaskRecordFunnelVO.TaskStatVO> taskList = new ArrayList<>();
        for (Map<String, Object> stat : taskStats) {
            TaskRecordFunnelVO.TaskStatVO item = new TaskRecordFunnelVO.TaskStatVO();
            Object idValue = stat.get("taskConfigId");
            Long configId = idValue == null ? null : ((Number) idValue).longValue();
            long recordCount = toLong(stat.get("recordCount"));
            long reached = toLong(stat.get("reachedCount"));
            item.setTaskConfigId(configId);
            item.setRecordCount(recordCount);
            item.setMemberCount(toLong(stat.get("memberCount")));
            item.setReachedCount(reached);
            item.setReachRate(rate(reached, recordCount));
            item.setStaleRunningCount(toLong(stat.get("staleRunningCount")));
            TaskConfig config = configId == null ? null : configMap.get(configId);
            if (config != null) {
                item.setTaskName(config.getTaskName());
                item.setTaskGroup(config.getTaskGroup());
            }
            taskList.add(item);
        }
        vo.setTaskList(taskList);
        return vo;
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(TaskRecordAddForm addForm) {
        TaskRecord taskRecord = SmartBeanUtil.copy(addForm, TaskRecord.class);
        taskRecordDao.insert(taskRecord);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(TaskRecordUpdateForm updateForm) {
        TaskRecord taskRecord = SmartBeanUtil.copy(updateForm, TaskRecord.class);
        taskRecordDao.updateById(taskRecord);
        return ResponseDTO.ok();
    }

    /**
     * 任务记录 批量禁用：置为 3-已过期。
     *
     * <p>t_task_record.status 没有「禁用」这一档，「让这条记录不再推进、不再发奖」在库里
     * 只有「已过期」一个终态可表达（过期任务本来也是这么收口的）。
     * 故这里只放行 3，不接受其它值 —— 允许管理端随手把记录改回「进行中」或「已发奖」，
     * 等于给了一条绕过运行态直接改结果的路。
     */
    public ResponseDTO<String> updateStatus(TaskRecordStatusUpdateForm form) {
        if (!STATUS_EXPIRED.equals(form.getStatus())) {
            return ResponseDTO.userErrorParam("任务记录只支持置为 3-已过期（即管理端的「禁用」）");
        }
        for (Long id : form.getIdList()) {
            TaskRecord update = new TaskRecord();
            update.setId(id);
            update.setStatus(form.getStatus());
            taskRecordDao.updateById(update);
        }
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (SmartCollectionUtil.isEmpty(idList)){
            return ResponseDTO.ok();
        }

        taskRecordDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id){
            return ResponseDTO.ok();
        }

        taskRecordDao.deleteById(id);
        return ResponseDTO.ok();
    }

    private BigDecimal rate(long part, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(part).divide(BigDecimal.valueOf(total), RATE_SCALE, RoundingMode.HALF_UP);
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}
