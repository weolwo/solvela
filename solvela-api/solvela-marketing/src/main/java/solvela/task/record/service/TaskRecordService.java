package solvela.task.record.service;

import solvela.enums.TaskRecordStatusEnum;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import solvela.base.domain.PageResult;
import solvela.base.stat.Checkup;
import solvela.base.stat.Rate;
import solvela.base.stat.StatRow;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.util.SolvelaCollectionUtil;
import solvela.base.dao.SolvelaPageUtil;
import solvela.task.record.dao.TaskRecordDao;
import solvela.task.TaskRecord;
import solvela.task.record.domain.command.TaskRecordAddCommand;
import solvela.task.record.domain.query.TaskRecordQuery;
import solvela.task.record.domain.command.TaskRecordUpdateCommand;
import solvela.task.record.domain.dto.TaskRecordFunnelDTO;
import solvela.task.record.domain.dto.TaskRecordDTO;
import solvela.task.constant.TaskDiscardCode;
import solvela.task.TaskConfig;
import solvela.task.taskconfig.manager.TaskConfigManager;
import org.springframework.stereotype.Service;

import solvela.member.service.MemberService;
import solvela.exception.BusinessException;
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

    /** 只用来校验会员号真实存在（状态表不留账号快照） */
    private final MemberService memberService;
    private final TaskConfigManager taskConfigManager;

    /**
     * 分页查询
     */
    public PageResult<TaskRecordDTO> queryPage(TaskRecordQuery queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<TaskRecordDTO> list = taskRecordDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
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
    public TaskRecordFunnelDTO funnel(TaskRecordQuery queryForm) {
        StatRow row = StatRow.of(taskRecordDao.selectFunnel(queryForm));
        DiscardStats discards = discardStats(queryForm);

        TaskRecordFunnelDTO vo = new TaskRecordFunnelDTO();
        fillOverview(row, vo);
        vo.setDiscardTotalCount(discards.total());
        vo.setDiscardAttentionCount(discards.attention());
        vo.setDiscardList(discards.list());
        vo.setIssueList(checkup(row, discards.attention()));
        vo.setTaskList(taskStats(queryForm));
        return vo;
    }

    /**
     * 接取总量与四种去向（进行中 / 已完成 / 已发奖 / 已过期）。
     */
    private void fillOverview(StatRow row, TaskRecordFunnelDTO vo) {
        long total = row.count("totalCount");
        long members = row.count("memberCount");
        long completed = row.count("completedCount");
        long dispatched = row.count("dispatchedCount");

        vo.setTotalCount(total);
        vo.setMemberCount(members);
        vo.setRunningCount(row.count("runningCount"));
        vo.setCompletedCount(completed);
        vo.setDispatchedCount(dispatched);
        vo.setExpiredCount(row.count("expiredCount"));
        vo.setStaleRunningCount(row.count("staleRunningCount"));
        /*
         * 达标率的分母用「接取总数」而不是「已收口的」：任务不像开奖有明确的揭晓时刻，
         * 进行中本身就是一种结果（做不完也是做不完），把它剔出分母会让达标率虚高。
         */
        vo.setReachRate(Rate.share(completed + dispatched, total));
        vo.setRecordPerMember(Rate.average(total, members));
    }

    /**
     * 事件丢弃分布，以及它的两个合计。
     *
     * @param total     丢弃总量，同时是各原因占比的分母
     * @param attention 其中<b>需要人去处理</b>的那部分。正常业务拦截（没资格、已达上限）
     *                  数量远大于异常，只看总量会被它们淹没，所以异常量单独拎出来一个数
     */
    private record DiscardStats(long total, long attention, List<TaskRecordFunnelDTO.DiscardStatDTO> list) {
    }

    private DiscardStats discardStats(TaskRecordQuery queryForm) {
        List<StatRow> stats = StatRow.of(taskRecordDao.selectDiscardStat(queryForm));
        long total = stats.stream().mapToLong(s -> s.count("discardCount")).sum();

        long attention = 0L;
        List<TaskRecordFunnelDTO.DiscardStatDTO> discardList = new ArrayList<>();
        for (StatRow stat : stats) {
            String code = stat.text("discardCode");
            long count = stat.count("discardCount");
            TaskDiscardCode discardCode = code == null ? null : TaskDiscardCode.resolve(code);

            TaskRecordFunnelDTO.DiscardStatDTO item = new TaskRecordFunnelDTO.DiscardStatDTO();
            item.setDiscardCode(code);
            // 归不了类的一律直说，不要编一个像模像样的名字盖住「写入侧没写 discard_code」这件事
            item.setDiscardDesc(discardCode == null ? "未归类（写入侧没写 discard_code）" : discardCode.getDesc());
            item.setDiscardCount(count);
            item.setDiscardShare(Rate.share(count, total));
            item.setNeedsAttention(discardCode != null && discardCode.needsAttention());
            if (Boolean.TRUE.equals(item.getNeedsAttention())) {
                attention += count;
            }
            discardList.add(item);
        }
        return new DiscardStats(total, attention, discardList);
    }

    /**
     * 一致性 + 收口体检。
     */
    private List<String> checkup(StatRow row, long discardAttention) {
        return new Checkup()
                .countIf(row.count("staleRunningCount"),
                        "有 {} 条记录已过有效期却仍是「进行中」：工程里没有过期扫描任务"
                                + "（idx_t_tsk_rec_expire 就是给它建的），这些记录不会自己收口，"
                                + "用户端会一直看到一个永远完不成的任务。可先用「批量禁用」置为已过期")
                .countIf(row.count("completedCount"),
                        "有 {} 条记录停在「已完成」没有流转到「已发奖」："
                                + "达标闸门与发奖流转是紧邻的两条 SQL，正常不会停在中间，"
                                + "出现说明发奖那一步断了。请到「发奖记录」确认这些人的奖到底发出去没有")
                .countIf(row.count("reachedNoCompleteTime"),
                        "有 {} 条记录已达标却没有达标时间：客诉时说不清是哪天完成的，"
                                + "多半是人工改状态时没补 complete_time")
                .countIf(row.count("runningWithCompleteTime"),
                        "有 {} 条记录是「进行中」却带着达标时间：与状态自相矛盾，"
                                + "多半是被人工从已完成改回了进行中")
                .countIf(row.count("reachedNoPrizeSnapshot"),
                        "有 {} 条记录已达标但奖励快照是空的：接取时这个任务压根没配奖励，"
                                + "用户做完了也没有奖可发")
                .countIf(row.count("noRuleSnapshot"),
                        "有 {} 条记录的规则快照为空：策略层读不到目标值，"
                                + "这些记录进度能涨但永远不会达标（任务配置的 rule_config 为空导致）")
                .countIf(discardAttention,
                        "有 {} 条事件因「上游未告知会员属性 / 任务配置异常 / 系统繁忙被拒」被丢弃："
                                + "这三类不是正常业务拦截，要去找上游补字段、修配置或扩容，光看丢弃总量会被正常拦截淹没")
                .issues();
    }

    /**
     * 任务维度分布：哪个任务接得多、哪个任务没人做得完。
     */
    private List<TaskRecordFunnelDTO.TaskStatDTO> taskStats(TaskRecordQuery queryForm) {
        List<StatRow> stats = StatRow.of(taskRecordDao.selectTaskStat(queryForm));
        List<Long> configIds = stats.stream()
                .map(s -> s.id("taskConfigId"))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, TaskConfig> configMap = configIds.isEmpty() ? Map.of()
                : taskConfigManager.lambdaQuery().in(TaskConfig::getId, configIds).list().stream()
                        .collect(Collectors.toMap(TaskConfig::getId, Function.identity(), (first, ignored) -> first));

        List<TaskRecordFunnelDTO.TaskStatDTO> taskList = new ArrayList<>();
        for (StatRow stat : stats) {
            Long configId = stat.id("taskConfigId");
            long recordCount = stat.count("recordCount");
            long reached = stat.count("reachedCount");

            TaskRecordFunnelDTO.TaskStatDTO item = new TaskRecordFunnelDTO.TaskStatDTO();
            item.setTaskConfigId(configId);
            item.setRecordCount(recordCount);
            item.setMemberCount(stat.count("memberCount"));
            item.setReachedCount(reached);
            item.setReachRate(Rate.share(reached, recordCount));
            item.setStaleRunningCount(stat.count("staleRunningCount"));
            // 任务配置可能已被删除，那时只能显示 id
            TaskConfig config = configId == null ? null : configMap.get(configId);
            if (config != null) {
                item.setTaskName(config.getTaskName());
                item.setTaskGroup(config.getTaskGroup());
            }
            taskList.add(item);
        }
        return taskList;
    }

    /**
     * 添加
     */
    public void add(TaskRecordAddCommand addForm) {
        TaskRecord taskRecord = SolvelaBeanUtil.copy(addForm, TaskRecord.class);
        // 任务记录是状态表，不留账号快照；但会员号必须真实存在 ——
        // 关联键指向一个查不到的会员，列表里会是一行没有名字的孤儿记录，且当场不报错
        memberService.requireExists(addForm.getMemberId());
        taskRecordDao.insert(taskRecord);
    }

    /**
     * 更新
     *
     */
    public void update(TaskRecordUpdateCommand updateForm) {
        TaskRecord taskRecord = SolvelaBeanUtil.copy(updateForm, TaskRecord.class);
        taskRecordDao.updateById(taskRecord);
    }

    /**
     * 任务记录 批量禁用：置为 3-已过期。
     *
     * <p>t_task_record.status 没有「禁用」这一档，「让这条记录不再推进、不再发奖」在库里
     * 只有「已过期」一个终态可表达（过期任务本来也是这么收口的）。
     * 故这里只放行 3，不接受其它值 —— 允许管理端随手把记录改回「进行中」或「已发奖」，
     * 等于给了一条绕过运行态直接改结果的路。
     */
    public void updateStatus(List<Long> idList, TaskRecordStatusEnum status) {
        // 这条不是「取值合不合法」（那由枚举保证），而是一条业务规则：
        // 管理端只能把记录置为已过期，不能绕过运行态直接改成已完成/已发奖
        if (status != TaskRecordStatusEnum.EXPIRED) {
            throw new BusinessException("任务记录只支持置为「已过期」（即管理端的「禁用」）");
        }
        for (Long id : idList) {
            TaskRecord update = new TaskRecord();
            update.setId(id);
            update.setStatus(status);
            taskRecordDao.updateById(update);
        }
    }

    /**
     * 批量删除
     */
    public void batchDelete(List<Long> idList) {
        if (SolvelaCollectionUtil.isEmpty(idList)) {
            return;
        }

        taskRecordDao.deleteBatchIds(idList);
    }

    /**
     * 单个删除
     */
    public void delete(Long id) {
        if (null == id){
            return;
        }

        taskRecordDao.deleteById(id);
    }
}
