package solvela.draw.poolconfig.spi;

import lombok.RequiredArgsConstructor;
import solvela.activity.domain.vo.ActivityRefItem;
import solvela.activity.spi.ActivityRefProvider;
import solvela.draw.PrizePoolConfig;
import solvela.draw.poolconfig.manager.PrizePoolConfigManager;
import solvela.draw.PrizePoolItem;
import solvela.draw.poolitem.manager.PrizePoolItemManager;
import solvela.enums.ActivityTypeEnum;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 抽奖玩法的活动下游引用查询
 *
 * @Author weolwo
 * @Date 2026-07-29
 */
@Component
@RequiredArgsConstructor
public class DrawActivityRefProvider implements ActivityRefProvider {

    private final PrizePoolConfigManager prizePoolConfigManager;
    private final PrizePoolItemManager prizePoolItemManager;

    @Override
    public ActivityTypeEnum supportType() {
        return ActivityTypeEnum.DRAW;
    }

    @Override
    public List<ActivityRefItem> countRefs(String activityCode) {
        List<ActivityRefItem> refs = new ArrayList<>();
        long poolCount = prizePoolConfigManager.lambdaQuery()
                .eq(PrizePoolConfig::getActivityCode, activityCode).count();
        if (poolCount > 0) {
            refs.add(new ActivityRefItem("奖池", poolCount));
        }
        long itemCount = prizePoolItemManager.lambdaQuery()
                .eq(PrizePoolItem::getActivityCode, activityCode).count();
        if (itemCount > 0) {
            refs.add(new ActivityRefItem("奖池物资", itemCount));
        }
        return refs;
    }

    /** 抽奖的玩法主体是奖池 */
    @Override
    public long gameplayCount(String activityCode) {
        return prizePoolConfigManager.lambdaQuery()
                .eq(PrizePoolConfig::getActivityCode, activityCode).count();
    }

    /**
     * 抽奖的「配置完备」= 至少有一个奖池。
     *
     * 不必再校验概率闭环：PrizePoolConfigService.workbenchSave() 已经在保存时就用 BigDecimal
     * 逐池重算过闭环，闭环不成立的奖池根本存不进来。所以「库里有奖池」本身就蕴含了「它是配平的」，
     * 这里再算一遍只会多出一处可能与保存逻辑漂移的实现。
     */
    @Override
    public String checkConfigured(String activityCode) {
        boolean hasPool = prizePoolConfigManager.lambdaQuery()
                .eq(PrizePoolConfig::getActivityCode, activityCode).exists();
        return hasPool ? null : "尚未配置奖池";
    }
}
