package solvela.lottery.config.spi;

import lombok.RequiredArgsConstructor;
import solvela.activity.domain.dto.ActivityRefItem;
import solvela.activity.spi.ActivityRefProvider;
import solvela.enums.ActivityTypeEnum;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.util.SolvelaCodeUtil;
import solvela.enums.LotteryConfigStatusEnum;
import solvela.lottery.LotteryConfig;
import solvela.lottery.LotteryPrizeRule;
import solvela.lottery.prizerule.manager.LotteryPrizeRuleManager;
import solvela.lottery.config.manager.LotteryConfigManager;
import solvela.lottery.config.service.LotteryConfigService;
import solvela.lottery.LotteryIssue;
import solvela.lottery.issue.manager.LotteryIssueManager;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private final LotteryPrizeRuleManager lotteryPrizeRuleManager;

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

    /**
     * 复制彩票玩法：玩法配置 + 奖级规则。
     *
     * <h3>🔴 新玩法一律落「下线」</h3>
     * 这一条比别处更要紧：{@code TicketIssueService} 放行只看
     * 玩法状态 + 期号状态 + 售卖窗口，<b>根本不校验活动状态</b>。
     * 玩法状态照抄成上线的话，一个「未开始」的活动底下会真的能领号 ——
     * 而运营看着活动列表上那个「未开始」，完全不会想到去查这件事。
     *
     * <h3>期号不复制</h3>
     * {@code t_lottery_issue} 带着 {@code sold_count}，且已售号码在
     * {@code t_lottery_record} 里 —— 那是运行态数据，复制过来就是伪造发行记录。
     * 新活动的期号由运营在期号控制台自己开。
     */
    @Override
    public void copyTo(String sourceActivityCode, String targetActivityCode, Map<String, String> prizeCodeMap) {
        List<LotteryConfig> sourceList = lotteryConfigManager.lambdaQuery()
                .eq(LotteryConfig::getActivityCode, sourceActivityCode).list();
        for (LotteryConfig source : sourceList) {
            LotteryConfig copy = SolvelaBeanUtil.copy(source, LotteryConfig.class);
            copy.setId(null);
            copy.setActivityCode(targetActivityCode);
            copy.setLotteryCode(SolvelaCodeUtil.generateUniqueBizCode(
                    SolvelaCodeUtil.BizCodePrefix.LOTTERY, this::existsByLotteryCode));
            // 见方法注释：这一步不能省
            copy.setStatus(LotteryConfigStatusEnum.OFFLINE);
            copy.setCreateBy(null);
            copy.setCreateTime(null);
            copy.setUpdateBy(null);
            copy.setUpdateTime(null);
            lotteryConfigManager.save(copy);

            for (LotteryPrizeRule rule : lotteryPrizeRuleManager.lambdaQuery()
                    .eq(LotteryPrizeRule::getLotteryCode, source.getLotteryCode()).list()) {
                LotteryPrizeRule ruleCopy = SolvelaBeanUtil.copy(rule, LotteryPrizeRule.class);
                ruleCopy.setId(null);
                ruleCopy.setLotteryCode(copy.getLotteryCode());
                // 查不到就沿用原值：奖品被删过时保留原编码，比写 null 让奖级彻底失联好
                ruleCopy.setPrizeCode(prizeCodeMap.getOrDefault(rule.getPrizeCode(), rule.getPrizeCode()));
                ruleCopy.setCreateBy(null);
                ruleCopy.setCreateTime(null);
                ruleCopy.setUpdateBy(null);
                ruleCopy.setUpdateTime(null);
                lotteryPrizeRuleManager.save(ruleCopy);
            }
        }
    }

    private boolean existsByLotteryCode(String lotteryCode) {
        return lotteryConfigManager.lambdaQuery().eq(LotteryConfig::getLotteryCode, lotteryCode).exists();
    }
}
