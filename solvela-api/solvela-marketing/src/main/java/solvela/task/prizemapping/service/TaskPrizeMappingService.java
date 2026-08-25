package solvela.task.prizemapping.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import solvela.base.common.domain.PageResult;
import solvela.base.common.util.SolvelaCollectionUtil;
import solvela.base.common.util.SolvelaPageUtil;
import solvela.task.constant.TaskConst;
import solvela.task.prizemapping.dao.TaskPrizeMappingDao;
import solvela.task.prizemapping.domain.entity.TaskPrizeMapping;
import solvela.task.prizemapping.domain.form.TaskPrizeMappingQueryForm;
import solvela.task.prizemapping.domain.vo.TaskPrizeMappingVO;
import solvela.task.runtime.domain.TaskRuleConfig;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 任务阶段与奖励映射表 Service
 *
 * <p>只读。写入口只有任务向导，理由见 {@code TaskPrizeMappingController} 的类注释。
 *
 * @Author weolwo
 * @Date 2026-04-18 20:41:02
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class TaskPrizeMappingService {

    private final TaskPrizeMappingDao taskPrizeMappingDao;

    /**
     * 分页查询（带配置体检）
     */
    public PageResult<TaskPrizeMappingVO> queryPage(TaskPrizeMappingQueryForm queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<TaskPrizeMappingVO> list = taskPrizeMappingDao.queryPage(page, queryForm);
        enrich(list);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }

    /**
     * 拆 JSON + 体检。
     *
     * <p>拆包格式与 {@code TaskConfigService.buildMappingList} 的落库格式一一对应，
     * 数值归一化直接复用 {@link TaskRuleConfig#decimal} —— 和运行态
     * {@code TaskPrizeDispatcher} 走同一条解析路径。这里另写一份「差不多的」解析，
     * 迟早会出现「巡检页显示正常、运行态解析不出」的漂移。
     */
    private void enrich(List<TaskPrizeMappingVO> list) {
        if (SolvelaCollectionUtil.isEmpty(list)) {
            return;
        }
        for (TaskPrizeMappingVO vo : list) {
            vo.setStageTarget(TaskRuleConfig.parse(vo.getStageCondition()).decimal(TaskConst.STAGE_KEY_TARGET));
            vo.setPrizeValue(TaskRuleConfig.parse(vo.getPrizeStrategy()).decimal(TaskConst.PRIZE_STRATEGY_KEY_VALUE));
        }

        Map<Long, List<TaskPrizeMapping>> ladderMap = loadFullLadders(list);
        for (TaskPrizeMappingVO vo : list) {
            vo.setIssueList(checkIssues(vo, ladderMap.get(vo.getTaskConfigId())));
        }
    }

    /**
     * 取当前页涉及的任务的<b>完整</b>阶梯。
     *
     * <p>「断档」「达标值不递增」是阶梯整体的性质，只看当前页的行会误判 ——
     * 分页正好把一条阶梯切成两半时，两页各自看都像是缺了档。
     */
    private Map<Long, List<TaskPrizeMapping>> loadFullLadders(List<TaskPrizeMappingVO> list) {
        Set<Long> taskConfigIdSet = list.stream()
                .map(TaskPrizeMappingVO::getTaskConfigId)
                .collect(Collectors.toSet());
        List<TaskPrizeMapping> all = taskPrizeMappingDao.selectList(
                Wrappers.<TaskPrizeMapping>lambdaQuery()
                        .in(TaskPrizeMapping::getTaskConfigId, taskConfigIdSet));

        Map<Long, List<TaskPrizeMapping>> ladderMap = new HashMap<>();
        for (TaskPrizeMapping mapping : all) {
            ladderMap.computeIfAbsent(mapping.getTaskConfigId(), k -> new ArrayList<>()).add(mapping);
        }
        ladderMap.values().forEach(ladder -> ladder.sort(Comparator.comparing(TaskPrizeMapping::getStageLevel)));
        return ladderMap;
    }

    /**
     * 一档配置的体检结论。
     *
     * <p>这页比向导多出来的价值就在这里：向导只看得见自己那一个任务，看不见跨对象的不一致。
     * 下面几条<b>都不会在配置时报错</b>，全是到用户真的达标那一刻才在日志里冒出来的问题。
     *
     * @param ladder 该任务的完整阶梯（理论上不会为 null，列表行就是从里面查出来的）
     */
    private List<String> checkIssues(TaskPrizeMappingVO vo, List<TaskPrizeMapping> ladder) {
        List<String> issues = new ArrayList<>();

        // 奖品对不上：PrizeConfigService.getByActivityCodeAndPrizeCode 返回 null 时，
        // TaskPrizeDispatcher 只 log.error 然后 return false，这一档永远发不出去
        if (vo.getPrizeName() == null) {
            issues.add("奖品配置不存在：活动 " + vo.getActivityCode() + " 下没有奖励编码 " + vo.getPrizeCode()
                    + "，用户达标时这一档会被静默跳过");
        }

        // 达标条件解析不出目标值：resolveStageTarget 返回 null，派发时 continue 掉
        if (vo.getStageTarget() == null) {
            issues.add("达标条件里取不到 target，运行态判不了这一档是否达标，会被跳过");
        }

        // RATIO / FORMULA 在 TaskPrizeDispatcher.resolvePrizeValue 里没有实现，
        // 会退回奖品配置的 prize_value —— 配了不报错，但发出来的不是运营以为的那个数
        if (!TaskConst.PRIZE_MODE_FIXED.equals(vo.getPrizeMode())) {
            issues.add("计算类型 " + vo.getPrizeMode() + " 尚未实现，实际按奖品配置的固定值发放");
        }

        if (ladder == null || ladder.size() <= 1) {
            return issues;
        }
        int index = indexOf(ladder, vo.getId());
        if (index <= 0) {
            return issues;
        }
        TaskPrizeMapping prev = ladder.get(index - 1);

        // 断档：uk_task_stage 只保证 (task_config_id, stage_level) 不重复，不保证连号。
        // 中间少一档不影响发奖，但基本可以断定是被人删掉的 —— 值得让运营看见
        if (ladder.get(index).getStageLevel() - prev.getStageLevel() != 1) {
            issues.add("阶梯层级不连续：上一档是第 " + prev.getStageLevel() + " 档");
        }

        // 达标值不递增：dispatchReachedStages 是「一次事件跨过几档就发几档」，
        // 后一档门槛不高于前一档，等于用户一到前一档就把后面的奖一起领了
        BigDecimal prevTarget = TaskRuleConfig.parse(prev.getStageCondition()).decimal(TaskConst.STAGE_KEY_TARGET);
        if (vo.getStageTarget() != null && prevTarget != null && vo.getStageTarget().compareTo(prevTarget) <= 0) {
            issues.add("达标值未高于上一档（上一档 " + prevTarget.toPlainString() + "），达标时会连同本档一起发放");
        }
        return issues;
    }

    private int indexOf(List<TaskPrizeMapping> ladder, Long id) {
        for (int i = 0; i < ladder.size(); i++) {
            if (ladder.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }
}
