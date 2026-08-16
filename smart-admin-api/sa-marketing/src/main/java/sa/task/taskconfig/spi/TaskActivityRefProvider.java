package sa.task.taskconfig.spi;

import lombok.RequiredArgsConstructor;
import sa.activity.domain.vo.ActivityRefItem;
import sa.activity.spi.ActivityRefProvider;
import sa.enums.ActivityTypeEnum;
import sa.task.taskconfig.domain.entity.TaskConfig;
import sa.task.taskconfig.manager.TaskConfigManager;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
}
