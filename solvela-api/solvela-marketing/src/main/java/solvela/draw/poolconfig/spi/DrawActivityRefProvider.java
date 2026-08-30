package solvela.draw.poolconfig.spi;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.util.SolvelaCodeUtil;
import solvela.draw.PoolPrizeMapping;
import solvela.draw.prizemapping.manager.PoolPrizeMappingManager;
import solvela.activity.domain.dto.ActivityRefItem;
import solvela.activity.spi.ActivityRefProvider;
import solvela.draw.PrizePoolConfig;
import solvela.draw.poolconfig.manager.PrizePoolConfigManager;
import solvela.draw.PrizePoolItem;
import solvela.draw.poolitem.manager.PrizePoolItemManager;
import solvela.enums.ActivityTypeEnum;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 抽奖玩法的活动下游引用查询
 *
 * @Author weolwo
 * @Date 2026-07-29
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DrawActivityRefProvider implements ActivityRefProvider {

    private final PrizePoolConfigManager prizePoolConfigManager;
    private final PrizePoolItemManager prizePoolItemManager;
    private final PoolPrizeMappingManager poolPrizeMappingManager;

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

    /**
     * 复制抽奖玩法：奖池 + 奖池物资 + 概率映射，三张表一起。
     *
     * <h3>两处必须重映射，漏一处新活动就会指向老活动的东西</h3>
     * <ol>
     *   <li>物资上的 {@code prize_code}：按调用方给的奖品编码映射重指向；</li>
     *   <li>映射表上的 {@code prize_item_id}：它引用的是物资的<b>自增主键</b>，
     *       所以必须先把物资插完、拿到新旧 ID 的对应关系，再插映射。</li>
     * </ol>
     *
     * <h3>库存水位与乐观锁版本号归零</h3>
     * {@code used_stock} 抄过来，新活动开局库存就少一半，而页面上只显示「总库存」，
     * 完全看不出来。{@code version} 同理，它是乐观锁的计数器，跟业务无关。
     */
    @Override
    public void copyTo(String sourceActivityCode, String targetActivityCode, Map<String, String> prizeCodeMap) {
        List<PrizePoolConfig> pools = prizePoolConfigManager.lambdaQuery()
                .eq(PrizePoolConfig::getActivityCode, sourceActivityCode).list();
        if (pools.isEmpty()) {
            return;
        }

        // 1. 奖池：重新发 pool_code
        Map<String, String> poolCodeMap = new LinkedHashMap<>();
        for (PrizePoolConfig source : pools) {
            PrizePoolConfig copy = SolvelaBeanUtil.copy(source, PrizePoolConfig.class);
            copy.setId(null);
            copy.setActivityCode(targetActivityCode);
            copy.setPoolCode(SolvelaCodeUtil.generateUniqueBizCode(
                    SolvelaCodeUtil.BizCodePrefix.POOL, this::existsByPoolCode));
            clearAudit(copy);
            prizePoolConfigManager.save(copy);
            poolCodeMap.put(source.getPoolCode(), copy.getPoolCode());
        }

        // 2. 物资：prize_code 重映射 + 水位归零。同时记下新旧主键，第 3 步要用
        List<PrizePoolItem> items = prizePoolItemManager.lambdaQuery()
                .eq(PrizePoolItem::getActivityCode, sourceActivityCode).list();
        Map<Long, Long> itemIdMap = new LinkedHashMap<>();
        for (PrizePoolItem source : items) {
            PrizePoolItem copy = SolvelaBeanUtil.copy(source, PrizePoolItem.class);
            copy.setId(null);
            copy.setActivityCode(targetActivityCode);
            // 映射里查不到就沿用原值：奖品配置被删过时会出现这种情况，
            // 此时保留原编码比写一个 null 进去好 —— 后者会让物资彻底失联且不报错
            copy.setPrizeCode(prizeCodeMap.getOrDefault(source.getPrizeCode(), source.getPrizeCode()));
            copy.setUsedStock(0);
            copy.setVersion(0);
            clearAudit(copy);
            prizePoolItemManager.save(copy);
            itemIdMap.put(source.getId(), copy.getId());
        }

        // 3. 概率映射：pool_code 与 prize_item_id 双重映射
        List<String> sourcePoolCodes = pools.stream().map(PrizePoolConfig::getPoolCode).toList();
        List<PoolPrizeMapping> mappings = poolPrizeMappingManager.lambdaQuery()
                .in(PoolPrizeMapping::getPoolCode, sourcePoolCodes).list();
        for (PoolPrizeMapping source : mappings) {
            Long newItemId = itemIdMap.get(source.getPrizeItemId());
            String newPoolCode = poolCodeMap.get(source.getPoolCode());
            if (newItemId == null || newPoolCode == null) {
                // 指向已经不存在的物资/奖池的孤儿映射，跳过而不是带一条断链过去
                log.warn("【活动复制】跳过孤儿概率映射：poolCode={}, prizeItemId={}",
                        source.getPoolCode(), source.getPrizeItemId());
                continue;
            }
            PoolPrizeMapping copy = SolvelaBeanUtil.copy(source, PoolPrizeMapping.class);
            copy.setId(null);
            copy.setPoolCode(newPoolCode);
            copy.setPrizeItemId(newItemId);
            clearAudit(copy);
            poolPrizeMappingManager.save(copy);
        }
    }

    private boolean existsByPoolCode(String poolCode) {
        return prizePoolConfigManager.lambdaQuery().eq(PrizePoolConfig::getPoolCode, poolCode).exists();
    }

    /** 审计列交给自动填充与 DDL 默认值，不抄源记录的 —— 抄了就是「新建的记录写着别人半年前的创建时间」 */
    private void clearAudit(Object entity) {
        if (entity instanceof PrizePoolConfig e) {
            e.setCreateBy(null); e.setCreateTime(null); e.setUpdateBy(null); e.setUpdateTime(null);
        } else if (entity instanceof PrizePoolItem e) {
            e.setCreateBy(null); e.setCreateTime(null); e.setUpdateBy(null); e.setUpdateTime(null);
        } else if (entity instanceof PoolPrizeMapping e) {
            e.setCreateBy(null); e.setCreateTime(null); e.setUpdateBy(null); e.setUpdateTime(null);
        }
    }
}
