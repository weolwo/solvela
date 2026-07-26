package net.lab1024.sa.draw.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import net.lab1024.sa.draw.drawlog.dao.DrawPrizeLogDao;
import net.lab1024.sa.draw.drawlog.domain.entity.DrawPrizeLog;
import net.lab1024.sa.draw.engine.DrawEngine;
import net.lab1024.sa.draw.engine.DrawPoolSnapshot;
import net.lab1024.sa.draw.engine.DrawPrizeSnapshot;
import net.lab1024.sa.draw.engine.DrawResult;
import net.lab1024.sa.draw.engine.ProbabilityRange;
import net.lab1024.sa.draw.poolconfig.domain.entity.PrizePoolConfig;
import net.lab1024.sa.draw.poolconfig.manager.PrizePoolConfigManager;
import net.lab1024.sa.draw.poolitem.dao.PrizePoolItemDao;
import net.lab1024.sa.draw.poolitem.domain.entity.PrizePoolItem;
import net.lab1024.sa.draw.poolitem.manager.PrizePoolItemManager;
import net.lab1024.sa.draw.prizemapping.domain.entity.PoolPrizeMapping;
import net.lab1024.sa.draw.prizemapping.manager.PoolPrizeMappingManager;
import net.lab1024.sa.draw.runtime.domain.DrawExecuteForm;
import net.lab1024.sa.draw.runtime.domain.DrawExecuteVO;
import net.lab1024.sa.domain.event.UserPrizeEvent;
import net.lab1024.sa.enums.PrizeTypeEnum;
import net.lab1024.sa.ledger.wallet.service.MemberWalletService;
import net.lab1024.sa.prize.prizeconfig.domain.entity.PrizeConfig;
import net.lab1024.sa.prize.prizeconfig.service.PrizeConfigService;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 抽奖执行编排（运行态核心链路）
 *
 * 流程：幂等防重 -> 组装奖池快照 -> DrawEngine 纯函数判定 -> Redis Lua 原子预扣(防超发第一道)
 *      -> DB 条件更新库存(防超发第二道，失败补偿回滚 Redis) -> 落抽奖流水
 *
 * TODO 后续接入：RRateLimiter 防刷限流、抽奖资格扣减(积分/门票走 ledger)、中奖后异步派发(consumer -> risk -> ledger)
 *
 * @Author alaric
 * @Date 2026-07-26
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class DrawExecuteService {

    private final PrizePoolConfigManager prizePoolConfigManager;
    private final PoolPrizeMappingManager poolPrizeMappingManager;
    private final PrizePoolItemManager prizePoolItemManager;
    private final PrizePoolItemDao prizePoolItemDao;
    private final DrawPrizeLogDao drawPrizeLogDao;
    private final DrawStockService drawStockService;
    private final RedissonClient redissonClient;
    private final MemberWalletService memberWalletService;
    private final PrizeConfigService prizeConfigService;
    private final ApplicationEventPublisher applicationEventPublisher;

    private static final Integer POOL_STATUS_OPEN = 1;
    private static final int UNLIMITED = -1;

    /**
     * 奖池消耗资产类型（对齐 t_prize_pool_config.cost_asset_type）
     */
    private static final String COST_TYPE_CREDIT = "CREDIT";
    private static final String COST_TYPE_TICKET = "TICKET";
    private static final String COST_TYPE_NONE = "NONE";
    /**
     * 抽奖消耗/退还的流水业务类型
     */
    private static final String BIZ_TYPE_DRAW_COST = "DRAW_COST";
    private static final String BIZ_TYPE_DRAW_COST_REFUND = "DRAW_COST_REFUND";
    /**
     * 防刷限流：单用户单活动每秒最多 2 次；限流器 key 一小时无活动自动过期
     */
    private static final long RATE_LIMIT_PER_INTERVAL = 2;
    private static final long RATE_LIMIT_INTERVAL_SECONDS = 1;
    private static final Duration RATE_LIMITER_TTL = Duration.ofHours(1);
    /**
     * 抽奖流水状态（对齐 t_draw_prize_log.status 注释）
     */
    private static final int LOG_STATUS_HIT = 1;
    private static final int LOG_STATUS_NO_STOCK = 2;
    /**
     * 幂等标记有效期：覆盖常见网络重试窗口
     */
    private static final Duration REQUEST_DEDUP_TTL = Duration.ofMinutes(10);

    /**
     * 事务边界说明：DB 写入（资格扣减/流水/库存兜底）同一事务；
     * UserPrizeEvent 经 @TransactionalEventListener(AFTER_COMMIT) 在提交后才真正派发，天然防「事务回滚但奖已发」
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<DrawExecuteVO> execute(DrawExecuteForm form) {
        // 1. 幂等防重（调用方传 requestId 即启用）
        if (form.getRequestId() != null && !form.getRequestId().isBlank()) {
            boolean first = redissonClient.getBucket(DrawCacheKey.request(form.getRequestId()), StringCodec.INSTANCE)
                    .setIfAbsent("1", REQUEST_DEDUP_TTL);
            if (!first) {
                return ResponseDTO.userErrorParam("请求处理中或已处理，请勿重复提交");
            }
        }

        // 2. 防刷限流（单用户单活动）
        RRateLimiter limiter = redissonClient.getRateLimiter(
                DrawCacheKey.rateLimit(form.getActivityCode(), form.getMemberName()));
        if (limiter.trySetRate(RateType.OVERALL, RATE_LIMIT_PER_INTERVAL, RATE_LIMIT_INTERVAL_SECONDS, RateIntervalUnit.SECONDS)) {
            limiter.expire(RATE_LIMITER_TTL);
        }
        if (!limiter.tryAcquire()) {
            return ResponseDTO.userErrorParam("操作太频繁，请稍后再试");
        }

        // 3. 奖池校验
        PrizePoolConfig pool = prizePoolConfigManager.lambdaQuery()
                .eq(PrizePoolConfig::getPoolCode, form.getPoolCode()).one();
        if (pool == null || !form.getActivityCode().equals(pool.getActivityCode())) {
            return ResponseDTO.userErrorParam("奖池不存在或不属于该活动");
        }
        if (!POOL_STATUS_OPEN.equals(pool.getStatus())) {
            return ResponseDTO.userErrorParam("奖池未开启");
        }

        String traceId = form.getRequestId() != null && !form.getRequestId().isBlank()
                ? form.getRequestId() : UUID.randomUUID().toString().replace("-", "");

        // 4. 抽奖资格扣减（按奖池门票配置；余额不足由 BusinessException 统一转译给前端）
        if (COST_TYPE_TICKET.equals(pool.getCostAssetType())) {
            return ResponseDTO.userErrorParam("抽奖券消耗类型暂未开放，请将奖池配置为积分或无消耗");
        }
        PrizeTypeEnum costAsset = deductDrawCost(form, pool, traceId);

        // 5. 组装快照（配置读 DB，库存读 Redis；缓存未预热则回源并预热）
        List<PoolPrizeMapping> mappings = poolPrizeMappingManager.lambdaQuery()
                .eq(PoolPrizeMapping::getPoolCode, form.getPoolCode())
                .orderByAsc(PoolPrizeMapping::getSortWeight).list();
        if (mappings.isEmpty()) {
            return ResponseDTO.userErrorParam("奖池未配置奖项");
        }
        List<Long> itemIds = mappings.stream().map(PoolPrizeMapping::getPrizeItemId).toList();
        Map<Long, PrizePoolItem> itemMap = prizePoolItemManager.listByIds(itemIds).stream()
                .collect(Collectors.toMap(PrizePoolItem::getId, Function.identity()));

        List<DrawPrizeSnapshot> prizes = new ArrayList<>(mappings.size());
        List<java.math.BigDecimal> probabilities = new ArrayList<>(mappings.size());
        for (PoolPrizeMapping mapping : mappings.stream()
                .sorted(Comparator.comparing(PoolPrizeMapping::getSortWeight)).toList()) {
            PrizePoolItem item = itemMap.get(mapping.getPrizeItemId());
            if (item == null) {
                return ResponseDTO.userErrorParam("奖池配置异常：奖项已被删除，itemId=" + mapping.getPrizeItemId());
            }
            prizes.add(new DrawPrizeSnapshot(
                    item.getId(),
                    item.getPrizeCode(),
                    Integer.valueOf(1).equals(mapping.getIsFallback()),
                    resolveRemainStock(form.getActivityCode(), item),
                    parseWhiteList(item.getWhiteList())));
            probabilities.add(mapping.getProbability());
        }
        DrawPoolSnapshot snapshot = DrawPoolSnapshot.of(form.getPoolCode(), prizes, probabilities);

        // 6. 纯函数判定 + 双层扣减 + 落流水（sealed + 模式匹配消费）
        return switch (DrawEngine.draw(snapshot, form.getMemberName())) {
            case DrawResult.Hit(DrawPrizeSnapshot prize, DrawResult.HitSource source) ->
                    settle(form, traceId, snapshot, itemMap, prize, source, pool, costAsset);
            case DrawResult.NoStock(String candidateCode) -> {
                saveLog(form, traceId, null, candidateCode, LOG_STATUS_NO_STOCK, "快照判定无库存");
                refundDrawCost(form, pool, costAsset, traceId);
                yield ResponseDTO.ok(DrawExecuteVO.ofMiss("手慢了，奖品已被抽完"));
            }
        };
    }

    /**
     * 抽奖资格扣减：CREDIT(积分)映射钱包 SCORE 资产；NONE 无消耗；TICKET 待抽奖券资产建模后支持
     *
     * @return 实际扣减的资产类型，未扣减返回 null（供 NoStock 时退还判断）
     */
    private PrizeTypeEnum deductDrawCost(DrawExecuteForm form, PrizePoolConfig pool, String traceId) {
        String costType = pool.getCostAssetType();
        BigDecimal costValue = pool.getCostValue();
        if (costType == null || COST_TYPE_NONE.equals(costType) || costValue == null || costValue.signum() <= 0) {
            return null;
        }
        // CREDIT -> 钱包积分资产
        memberWalletService.executeWalletDeduct(form.getMemberName(), PrizeTypeEnum.SCORE, costValue,
                BIZ_TYPE_DRAW_COST, traceId, "抽奖消耗[" + pool.getPoolCode() + "]");
        return PrizeTypeEnum.SCORE;
    }

    /**
     * 系统无货导致本次抽奖未产生任何结果时，退还已扣的抽奖资格（同一事务，流水双向留痕）
     */
    private void refundDrawCost(DrawExecuteForm form, PrizePoolConfig pool, PrizeTypeEnum costAsset, String traceId) {
        if (costAsset == null) {
            return;
        }
        memberWalletService.executeWalletRefund(form.getMemberName(), costAsset, pool.getCostValue(),
                BIZ_TYPE_DRAW_COST_REFUND, traceId, "抽奖无货退还[" + pool.getPoolCode() + "]");
    }

    /**
     * 中奖后发布派发事件：AFTER_COMMIT 投递到 consumer -> risk -> ledger 公共链路
     * traceId 作为 sourceBizId，配合 t_prize_log 唯一索引做跨系统防重
     */
    private void publishPrizeEvent(DrawExecuteForm form, String traceId, String prizeCode) {
        PrizeConfig prizeConfig = prizeConfigService.getByPrizeCode(prizeCode);
        if (prizeConfig == null) {
            log.error("[抽奖派发] 奖品配置不存在，跳过派发: {}", prizeCode);
            return;
        }
        UserPrizeEvent event = UserPrizeEvent.builder()
                .sourceBizId(traceId)
                .activityCode(form.getActivityCode())
                .memberName(form.getMemberName())
                .prizeCode(prizeCode)
                .prizeType(prizeConfig.getPrizeType())
                .prizeValue(prizeConfig.getPrizeValue() == null ? null : prizeConfig.getPrizeValue().toPlainString())
                .prizeName(prizeConfig.getPrizeName())
                .build();
        applicationEventPublisher.publishEvent(event);
    }

    /**
     * 结算：Lua 预扣 -> DB 条件更新兜底 -> 失败降级兜底奖项 -> 落流水
     */
    private ResponseDTO<DrawExecuteVO> settle(DrawExecuteForm form, String traceId, DrawPoolSnapshot snapshot,
                                              Map<Long, PrizePoolItem> itemMap,
                                              DrawPrizeSnapshot candidate, DrawResult.HitSource source,
                                              PrizePoolConfig pool, PrizeTypeEnum costAsset) {
        if (tryDeduct(form, itemMap, candidate)) {
            saveLog(form, traceId, candidate.prizeItemId(), candidate.prizeCode(), LOG_STATUS_HIT, source.name());
            publishPrizeEvent(form, traceId, candidate.prizeCode());
            return ResponseDTO.ok(DrawExecuteVO.ofHit(candidate.prizeItemId(), candidate.prizeCode(), source.name()));
        }

        // 候选奖项扣减失败（并发抢空/超单人限领）-> 降级兜底
        DrawPrizeSnapshot fallback = snapshot.fallbackPrize();
        if (fallback != null && fallback.prizeItemId() != candidate.prizeItemId() && tryDeduct(form, itemMap, fallback)) {
            saveLog(form, traceId, fallback.prizeItemId(), fallback.prizeCode(), LOG_STATUS_HIT,
                    DrawResult.HitSource.FALLBACK_DEGRADE.name());
            publishPrizeEvent(form, traceId, fallback.prizeCode());
            return ResponseDTO.ok(DrawExecuteVO.ofHit(fallback.prizeItemId(), fallback.prizeCode(),
                    DrawResult.HitSource.FALLBACK_DEGRADE.name()));
        }

        saveLog(form, traceId, candidate.prizeItemId(), candidate.prizeCode(), LOG_STATUS_NO_STOCK, "预扣失败且兜底不可用");
        refundDrawCost(form, pool, costAsset, traceId);
        return ResponseDTO.ok(DrawExecuteVO.ofMiss("手慢了，奖品已被抽完"));
    }

    /**
     * 双层扣减：Redis Lua 原子预扣扛并发；DB 条件更新做最终一致性兜底，DB 拒绝则补偿回滚 Redis
     */
    private boolean tryDeduct(DrawExecuteForm form, Map<Long, PrizePoolItem> itemMap, DrawPrizeSnapshot prize) {
        PrizePoolItem item = itemMap.get(prize.prizeItemId());
        int userMax = item == null || item.getUserMaxCount() == null ? UNLIMITED : item.getUserMaxCount();

        StockDeductResult redisResult = drawStockService.deduct(
                form.getActivityCode(), prize.prizeItemId(), form.getMemberName(), userMax);
        if (!redisResult.success()) {
            log.info("[抽奖预扣拒绝] member={}, prizeItemId={}, reason={}",
                    form.getMemberName(), prize.prizeItemId(), redisResult.message());
            return false;
        }

        try {
            int rows = prizePoolItemDao.increaseUsedStock(prize.prizeItemId());
            if (rows == 0) {
                // Redis 与 DB 不一致（如缓存被误预热），以 DB 为准并补偿
                drawStockService.rollback(form.getActivityCode(), prize.prizeItemId(), form.getMemberName());
                log.warn("[抽奖DB兜底拒绝] prizeItemId={}, Redis已回滚", prize.prizeItemId());
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            drawStockService.rollback(form.getActivityCode(), prize.prizeItemId(), form.getMemberName());
            throw e;
        }
    }

    private void saveLog(DrawExecuteForm form, String traceId, Long prizeItemId, String prizeCode,
                         int status, String remark) {
        DrawPrizeLog logEntity = new DrawPrizeLog();
        logEntity.setTraceId(traceId);
        logEntity.setActivityCode(form.getActivityCode());
        logEntity.setPoolCode(form.getPoolCode());
        logEntity.setMemberName(form.getMemberName());
        logEntity.setPrizeItemId(prizeItemId == null ? 0L : prizeItemId);
        logEntity.setPrizeCode(prizeCode);
        logEntity.setStatus(status);
        logEntity.setRemark(remark);
        drawPrizeLogDao.insert(logEntity);
    }

    /**
     * 库存快照：优先读 Redis；未预热则按 DB 计算并回填
     */
    private int resolveRemainStock(String activityCode, PrizePoolItem item) {
        Integer cached = drawStockService.getRemainStock(activityCode, item.getId());
        if (cached != null) {
            return cached;
        }
        drawStockService.warmStock(activityCode, List.of(item));
        return UNLIMITED == item.getTotalStock()
                ? UNLIMITED
                : Math.max(0, item.getTotalStock() - (item.getUsedStock() == null ? 0 : item.getUsedStock()));
    }

    private Set<String> parseWhiteList(String whiteListJson) {
        if (whiteListJson == null || whiteListJson.isBlank()) {
            return Set.of();
        }
        List<String> list = JsonUtils.parseType(whiteListJson, new TypeReference<List<String>>() {
        });
        return list == null ? Set.of() : Set.copyOf(list);
    }
}
