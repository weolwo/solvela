package solvela.draw.drawconfig.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solvela.activity.ActivityConfig;
import solvela.activity.manager.ActivityConfigManager;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.util.SolvelaCodeUtil;
import solvela.draw.drawconfig.domain.dto.DrawConfigDTO;
import solvela.draw.DrawConfig;
import solvela.draw.PrizePoolConfig;
import solvela.draw.drawconfig.manager.DrawConfigManager;
import solvela.draw.poolconfig.manager.PrizePoolConfigManager;
import solvela.enums.DrawModeEnum;
import solvela.enums.EnableStatusEnum;
import solvela.draw.runtime.DrawPeriodResolver;
import solvela.exception.BusinessException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 抽奖配置的读写。
 *
 * <h3>为什么运行期读它要经过奖池</h3>
 * 抽奖执行时手里拿的是 {@code poolCode}（脚本挑的），要拿到重置周期就得先知道这个池属于哪套抽奖。
 * {@code t_prize_pool_config.draw_code} 就是那条边。
 *
 * <p>⚠️ 存量奖池的 {@code draw_code} 可能为空（迁移之前建的、或迁移之后手工插的）。
 * 这种情况下退回<b>按活动查</b> —— 一个活动一套抽奖，这条退路总是能命中。
 * 两条路都查不到时按「不重置」处理，理由与 {@code DrawPeriodResolver} 一致：
 * 宁可少重置，多重置意味着用户能比配置多抽，是资损。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DrawConfigService {

    private final DrawConfigManager drawConfigManager;

    private final PrizePoolConfigManager prizePoolConfigManager;

    private final ActivityConfigManager activityConfigManager;

    /**
     * 按抽奖编码取
     */
    public DrawConfig getByDrawCode(String drawCode) {
        if (drawCode == null || drawCode.isBlank()) {
            return null;
        }
        return drawConfigManager.lambdaQuery()
                .eq(DrawConfig::getDrawCode, drawCode)
                .oneOpt().orElse(null);
    }

    /**
     * 按活动编码取。一个活动至多一条，由唯一键 {@code uk_draw_activity} 保证
     */
    public DrawConfig getByActivityCode(String activityCode) {
        if (activityCode == null || activityCode.isBlank()) {
            return null;
        }
        return drawConfigManager.lambdaQuery()
                .eq(DrawConfig::getActivityCode, activityCode)
                .oneOpt().orElse(null);
    }

    /**
     * 奖池所属的抽奖配置。取不到返回 null，调用方自己决定默认行为。
     */
    public DrawConfig getByPool(PrizePoolConfig pool) {
        if (pool == null) {
            return null;
        }
        DrawConfig byCode = getByDrawCode(pool.getDrawCode());
        if (byCode != null) {
            return byCode;
        }
        // 退路：奖池还没归属到抽奖配置上。一个活动一套抽奖，所以按活动查一定等价
        DrawConfig byActivity = getByActivityCode(pool.getActivityCode());
        if (byActivity == null) {
            log.warn("【抽奖配置缺失】奖池 {} 既没有 draw_code，活动 {} 下也没有抽奖配置 —— "
                            + "限领计数将按「不重置」处理", pool.getPoolCode(), pool.getActivityCode());
        }
        return byActivity;
    }

    public List<DrawConfig> listAll() {
        return drawConfigManager.lambdaQuery().orderByDesc(DrawConfig::getId).list();
    }

    /**
     * 列表：带活动名称与奖池数。
     *
     * <p>两个统计字段都是<b>批量查一次再在内存里合</b>，不在循环里逐条查 ——
     * 抽奖配置与活动是一一对应的，条数等于抽奖活动数，逐条查就是 N+1。
     */
    public List<DrawConfigDTO> listWithStats() {
        List<DrawConfig> configs = listAll();
        if (configs.isEmpty()) {
            return List.of();
        }
        List<String> activityCodes = configs.stream().map(DrawConfig::getActivityCode).distinct().toList();
        Map<String, String> activityNames = activityConfigManager.lambdaQuery()
                .in(ActivityConfig::getActivityCode, activityCodes)
                .list().stream()
                .collect(Collectors.toMap(ActivityConfig::getActivityCode, ActivityConfig::getActivityName, (a, b) -> a));

        List<String> drawCodes = configs.stream().map(DrawConfig::getDrawCode).toList();
        Map<String, Long> poolCounts = prizePoolConfigManager.lambdaQuery()
                .in(PrizePoolConfig::getDrawCode, drawCodes)
                .list().stream()
                .collect(Collectors.groupingBy(PrizePoolConfig::getDrawCode, Collectors.counting()));

        return configs.stream().map(config -> {
            DrawConfigDTO dto = SolvelaBeanUtil.copy(config, DrawConfigDTO.class);
            dto.setActivityName(activityNames.get(config.getActivityCode()));
            dto.setPoolCount(poolCounts.getOrDefault(config.getDrawCode(), 0L).intValue());
            return dto;
        }).toList();
    }

    /**
     * 生成一个没被占用的抽奖编码
     */
    public String generateDrawCode() {
        return SolvelaCodeUtil.generateUniqueBizCode(SolvelaCodeUtil.BizCodePrefix.DRAW_CONFIG,
                code -> drawConfigManager.lambdaQuery().eq(DrawConfig::getDrawCode, code).exists());
    }

    /**
     * 取这个活动的抽奖配置编码，没有就建一条。
     *
     * <p>给抽奖工作台用：工作台是「把一个抽奖活动配起来」的入口，
     * 要求运营先去另一个页面建配置再回来，只会造出一堆没有配置的孤儿奖池 ——
     * 而孤儿奖池的表现是「抽奖走不到它，单人限领也永远不重置」，且不报错。
     *
     * <p>⚠️ 自动建的这条 {@code createBy} 记成 {@code workbench}，
     * 好让「这条是谁建的」在库里一眼看得出来，而不是一个空值。
     * 名称与重置周期都是默认值，运营可以在抽奖配置页改。
     *
     * @return 该活动的 draw_code
     */
    @Transactional(rollbackFor = Exception.class)
    public String ensureForActivity(String activityCode) {
        DrawConfig existing = getByActivityCode(activityCode);
        if (existing != null) {
            return existing.getDrawCode();
        }
        DrawConfig config = new DrawConfig();
        config.setActivityCode(activityCode);
        config.setDrawCode(generateDrawCode());
        config.setDrawName(activityCode + "-抽奖");
        config.setDrawMode(DrawModeEnum.PROBABILITY);
        config.setResetPeriod(DrawPeriodResolver.PERIOD_DAY);
        config.setStatus(EnableStatusEnum.ENABLED);
        config.setCreateBy("workbench");
        config.setUpdateBy("workbench");
        drawConfigManager.save(config);
        log.info("【抽奖配置】活动 {} 没有抽奖配置，工作台已自动创建 {}", activityCode, config.getDrawCode());
        return config.getDrawCode();
    }

    /**
     * 新增。
     *
     * <p>「一个活动一套抽奖」由唯一键兜底，这里先查一次只是为了给出人话报错 ——
     * 让运营看到「这个活动已经有抽奖配置了」，而不是一个唯一键冲突的堆栈。
     */
    public Long add(DrawConfig config, String operator) {
        if (getByActivityCode(config.getActivityCode()) != null) {
            throw new BusinessException("活动 [" + config.getActivityCode() + "] 已经有抽奖配置了，一个活动只能有一套");
        }
        if (config.getDrawMode() == null) {
            config.setDrawMode(DrawModeEnum.PROBABILITY);
        }
        if (config.getStatus() == null) {
            config.setStatus(EnableStatusEnum.ENABLED);
        }
        config.setCreateBy(operator);
        config.setUpdateBy(operator);
        drawConfigManager.save(config);
        return config.getId();
    }

    /**
     * 修改。
     *
     * <p>🔴 {@code drawCode} 与 {@code activityCode} 不许改：前者是脚本挂载的引用键，
     * 改了等于把已有挂载指向一个不存在的对象，而挂载表不会跟着动；
     * 后者改了就是把整套抽奖搬到另一个活动下，那不是「编辑」该干的事。
     */
    public void update(DrawConfig config, String operator) {
        DrawConfig existing = drawConfigManager.getById(config.getId());
        if (existing == null) {
            throw new BusinessException("抽奖配置不存在");
        }
        config.setDrawCode(existing.getDrawCode());
        config.setActivityCode(existing.getActivityCode());
        config.setUpdateBy(operator);
        drawConfigManager.updateById(config);
    }

    /**
     * 删除。
     *
     * <p>底下还挂着奖池时拒绝：奖池的重置周期要从这里读，删了它们就没有周期可依。
     */
    public void delete(Long id) {
        DrawConfig existing = drawConfigManager.getById(id);
        if (existing == null) {
            return;
        }
        long pools = prizePoolConfigManager.lambdaQuery()
                .eq(PrizePoolConfig::getDrawCode, existing.getDrawCode())
                .count();
        if (pools > 0) {
            throw new BusinessException("这套抽奖下还有 " + pools + " 个奖池，请先处理奖池");
        }
        drawConfigManager.remove(Wrappers.<DrawConfig>lambdaQuery().eq(DrawConfig::getId, id));
    }
}
