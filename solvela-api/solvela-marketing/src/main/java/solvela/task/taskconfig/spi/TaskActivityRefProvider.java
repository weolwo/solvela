package solvela.task.taskconfig.spi;

import lombok.RequiredArgsConstructor;
import solvela.activity.domain.dto.ActivityRefItem;
import solvela.activity.spi.ActivityRefProvider;
import solvela.enums.ActivityTypeEnum;
import solvela.base.util.SolvelaBeanUtil;
import solvela.enums.TaskConfigStatusEnum;
import solvela.task.TaskConfig;
import solvela.task.TaskPrizeMapping;
import solvela.task.prizemapping.manager.TaskPrizeMappingManager;
import solvela.task.taskconfig.manager.TaskConfigManager;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 任务玩法的活动下游引用查询
 *
 * @Author weolwo
 * @Date 2026-07-29
 */
@Component
@RequiredArgsConstructor
public class TaskActivityRefProvider implements ActivityRefProvider {

    private final TaskConfigManager taskConfigManager;
    private final TaskPrizeMappingManager taskPrizeMappingManager;

    @Override
    public ActivityTypeEnum supportType() {
        return ActivityTypeEnum.TASK;
    }

    @Override
    public List<ActivityRefItem> countRefs(String activityCode) {
        List<ActivityRefItem> refs = new ArrayList<>();
        long taskCount = taskConfigManager.lambdaQuery()
                .eq(TaskConfig::getActivityCode, activityCode).count();
        if (taskCount > 0) {
            refs.add(new ActivityRefItem("任务", taskCount));
        }
        return refs;
    }

    /** 任务的玩法主体是任务配置 */
    @Override
    public long gameplayCount(String activityCode) {
        return taskConfigManager.lambdaQuery()
                .eq(TaskConfig::getActivityCode, activityCode).count();
    }

    /**
     * 任务的「配置完备」= 至少有一个任务配置。
     *
     * 这里<b>不存在「有主表没子表」的中间态</b>：TaskConfigService.wizardSubmit() 是单个
     * @Transactional，主表 insert 与奖励子表 insertBatch 在同一事务里一次性提交 ——
     * 要么都有要么都没有。而向导前几步的中间态存在浏览器 localStorage 草稿里，根本不写库。
     * 故这里判「有无记录」是充分的，与彩票的情况不同（那边 t_lottery_config 可以在没有
     * 奖级规则时先存下来，所以必须另判）。
     */
    @Override
    public String checkConfigured(String activityCode) {
        boolean hasTask = taskConfigManager.lambdaQuery()
                .eq(TaskConfig::getActivityCode, activityCode).exists();
        return hasTask ? null : "尚未配置任务";
    }

    /**
     * 复制任务玩法：任务配置 + 各任务的奖励映射。
     *
     * <p>任务配置本身没有业务编码（主键就是自增 id），所以不用重新发码 ——
     * 但奖励映射按 {@code task_config_id} 挂在任务上，必须先插任务、拿到新 id 再插奖励。
     *
     * <p>状态一律落 {@link TaskConfigStatusEnum#PENDING}（待生效）：
     * 复制出来的活动本身是「未开始」，任务却是生效中的话，口径就对不上了。
     */
    @Override
    public void copyTo(String sourceActivityCode, String targetActivityCode, Map<String, String> prizeCodeMap) {
        List<TaskConfig> sourceList = taskConfigManager.lambdaQuery()
                .eq(TaskConfig::getActivityCode, sourceActivityCode).list();
        for (TaskConfig source : sourceList) {
            TaskConfig copy = SolvelaBeanUtil.copy(source, TaskConfig.class);
            copy.setId(null);
            copy.setActivityCode(targetActivityCode);
            copy.setStatus(TaskConfigStatusEnum.PENDING);
            copy.setCreateBy(null);
            copy.setCreateTime(null);
            copy.setUpdateBy(null);
            copy.setUpdateTime(null);
            taskConfigManager.save(copy);

            for (TaskPrizeMapping mapping : taskPrizeMappingManager.lambdaQuery()
                    .eq(TaskPrizeMapping::getTaskConfigId, source.getId()).list()) {
                TaskPrizeMapping mappingCopy = SolvelaBeanUtil.copy(mapping, TaskPrizeMapping.class);
                mappingCopy.setId(null);
                mappingCopy.setTaskConfigId(copy.getId());
                // 查不到就沿用原值：奖品被删过时保留原编码，比写 null 让它彻底失联好
                mappingCopy.setPrizeCode(prizeCodeMap.getOrDefault(mapping.getPrizeCode(), mapping.getPrizeCode()));
                mappingCopy.setCreateBy(null);
                mappingCopy.setCreateTime(null);
                mappingCopy.setUpdateBy(null);
                mappingCopy.setUpdateTime(null);
                taskPrizeMappingManager.save(mappingCopy);
            }
        }
    }
}
