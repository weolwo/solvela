package solvela.lottery.config.spi;

import lombok.RequiredArgsConstructor;
import solvela.activity.domain.vo.ActivityRefItem;
import solvela.activity.spi.ActivityRefProvider;
import solvela.enums.ActivityTypeEnum;
import solvela.lottery.config.domain.entity.LotteryConfig;
import solvela.lottery.config.manager.LotteryConfigManager;
import solvela.lottery.config.service.LotteryConfigService;
import solvela.lottery.issue.domain.entity.LotteryIssue;
import solvela.lottery.issue.manager.LotteryIssueManager;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 彩票玩法的活动下游引用查询
 *
 * @Author weolwo
 * @Date 2026-07-29
 */
@Component
@RequiredArgsConstructor
public class LotteryActivityRefProvider implements ActivityRefProvider {

    private final LotteryConfigManager lotteryConfigManager;
    private final LotteryIssueManager lotteryIssueManager;
    private final LotteryConfigService lotteryConfigService;

    @Override
    public ActivityTypeEnum supportType() {
        return ActivityTypeEnum.LOTTERY;
    }

    @Override
    public List<ActivityRefItem> countRefs(String activityCode) {
        List<ActivityRefItem> refs = new ArrayList<>();
        List<LotteryConfig> configList = lotteryConfigManager.lambdaQuery()
                .eq(LotteryConfig::getActivityCode, activityCode).list();
        if (configList.isEmpty()) {
            return refs;
        }
        refs.add(new ActivityRefItem("彩票玩法", configList.size()));

        List<String> lotteryCodes = configList.stream().map(LotteryConfig::getLotteryCode).toList();
        long issueCount = lotteryIssueManager.lambdaQuery()
                .in(LotteryIssue::getLotteryCode, lotteryCodes).count();
        if (issueCount > 0) {
            refs.add(new ActivityRefItem("期号", issueCount));
        }
        return refs;
    }

    /** 彩票的玩法主体是 t_lottery_config —— 一个活动可以挂多个玩法（5位/7位各一个） */
    @Override
    public long gameplayCount(String activityCode) {
        return lotteryConfigManager.lambdaQuery()
                .eq(LotteryConfig::getActivityCode, activityCode).count();
    }

    /**
     * 彩票的「配置完备」= 活动下至少有一个玩法，<b>且每个玩法都能上线</b>。
     *
     * ⚠️ 这里必须用上线校验，不能只看 t_lottery_config 有没有记录：
     * 玩法完全可以在<b>没有配任何奖级规则</b>的情况下保存成功
     * （LotteryConfigService.online() 正是为此在上线前拦了一道）。
     * 只看「有记录」会把一个上不了线的半残废状态显示成「已配置」，比不显示更糟。
     *
     * 判据直接调 {@link LotteryConfigService#checkOnlineReady(String)}，与 online() 共用一份，
     * 不在这里另写一套。
     */
    @Override
    public String checkConfigured(String activityCode) {
        List<LotteryConfig> configList = lotteryConfigManager.lambdaQuery()
                .eq(LotteryConfig::getActivityCode, activityCode).list();
        if (configList.isEmpty()) {
            return "尚未配置彩票玩法";
        }
        for (LotteryConfig config : configList) {
            String notReady = lotteryConfigService.checkOnlineReady(config.getLotteryCode());
            if (notReady != null) {
                return "玩法「" + config.getLotteryName() + "」" + notReady;
            }
        }
        return null;
    }
}
