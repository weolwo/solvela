package solvela.draw.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import solvela.base.domain.ResponseDTO;
import solvela.base.json.JsonUtils;
import tools.jackson.core.type.TypeReference;
import solvela.draw.drawlog.dao.DrawPrizeLogDao;
import solvela.draw.drawlog.domain.entity.DrawPrizeLog;
import solvela.draw.engine.DrawEngine;
import solvela.draw.engine.DrawPoolSnapshot;
import solvela.draw.engine.DrawPrizeSnapshot;
import solvela.draw.engine.DrawResult;
import solvela.draw.engine.ProbabilityRange;
import solvela.draw.poolconfig.domain.entity.PrizePoolConfig;
import solvela.draw.poolconfig.manager.PrizePoolConfigManager;
import solvela.draw.poolitem.dao.PrizePoolItemDao;
import solvela.draw.poolitem.domain.entity.PrizePoolItem;
import solvela.draw.poolitem.manager.PrizePoolItemManager;
import solvela.draw.prizemapping.domain.entity.PoolPrizeMapping;
import solvela.draw.prizemapping.manager.PoolPrizeMappingManager;
import solvela.draw.runtime.domain.DrawExecuteForm;
import solvela.draw.runtime.domain.DrawExecuteVO;
import solvela.domain.event.UserPrizeEvent;
import solvela.enums.PrizeTypeEnum;
import solvela.ledger.wallet.service.MemberWalletService;
import solvela.member.service.MemberService;
import solvela.prize.prizeconfig.domain.entity.PrizeConfig;
import solvela.prize.prizeconfig.service.PrizeConfigService;
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
 * 流程：幂等防重 -> 防刷限流 -> 组装奖池快照 -> DrawEngine 纯函数判定 -> Redis Lua 原子预扣(防超发第一道)
 *      -> DB 条件更新库存(防超发第二道，失败补偿回滚 Redis) -> 落抽奖流水 -> 中奖异步派发
 *
 * <h3>没有资产扣减，是刻意的</h3>
 * 本模块是纯粹的<b>命中判定与库存派发引擎</b>：抽一次消耗多少积分、要不要门票、
 * 无货时退不退，全部由上游业务算完再调进来 —— 与「去哪个奖池抽」是同一类决策，
 * 引擎不该替业务做主。
 *
 * <p>此前奖池表上挂着 {@code cost_asset_type} / {@code cost_value}，
 * 由本链路在抽奖前扣钱包积分、无货时退还。已于 v3.42 移除，理由有三：
 * <ul>
 *   <li><b>定价是业务规则，不是奖池属性。</b>同一个奖池对新人免费、对老用户收 100 分，
 *       是完全正常的诉求；把单价钉死在奖池上，这类规则一条都写不出来；</li>
 *   <li><b>扣减语义被写死成了积分。</b>{@code cost_asset_type} 名义上支持 CREDIT/TICKET/NONE，
 *       实现里 TICKET 直接返回「暂未开放」，CREDIT 恒定映射钱包 SCORE ——
 *       三选一的配置项实际只有一个值可用；</li>
 *   <li><b>与彩票模块不一致。</b>{@code TicketIssueService} 早就是纯派发引擎，
 *       类注释明写「消耗多少积分、单人限购几张，都由上游业务算完再调进来」。
 *       两个同类引擎各写一套，迟早漂移。</li>
 * </ul>
 *
 * <p>⚠️ <b>契约变更</b>：本接口不再扣费，因此<b>也不再退费</b>。
 * 返回 {@code ofMiss（手慢了，奖品已被抽完）} 时上游若已扣过资产，需要自行退还 ——
 * 判断依据就是返回值，不需要额外接口。
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
    private final PrizeConfigService prizeConfigService;
    private final ApplicationEventPublisher applicationEventPublisher;
    /**
     * 会员号 -> 账号。一次抽奖只查一次，结果沿调用链传下去：
     * 白名单判定、流水快照、派发事件三处都要用，各查一次就是三次往返。
     */
    private final MemberService memberService;

    private static final Integer POOL_STATUS_OPEN = 1;
    private static final int UNLIMITED = -1;

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

        // 会员号必须真实存在；顺带把账号取回来，后面白名单判定、流水快照、派发事件都要用。
        // 一次请求只查这一次，不在每个用到名字的地方各查一次。
        String memberName = memberService.requireMemberName(form.getMemberId());

        // 2. 防刷限流（单用户单活动）
        // 🔴 限流 key 用会员号：账号可改，改完就是一个全新的 key，限流当场归零
        RRateLimiter limiter = redissonClient.getRateLimiter(
                DrawCacheKey.rateLimit(form.getActivityCode(), form.getMemberId()));
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

        // 4. 组装快照（配置读 DB，库存读 Redis；缓存未预热则回源并预热）
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

        /*
         * 5. 解析本次抽奖归属的限领周期桶。
         *
         * 只有「奖池配了按天/周/月重置」且「确实存在限领的奖项」时才去取数据库时钟 ——
         * 抽奖是热路径，一次额外往返能省则省。两个条件任一不成立，桶都恒为 ALL，
         * 与本次改动之前的行为完全一致。
         */
        DrawPeriodResolver.Period period = resolvePeriod(pool, itemMap);

        // 6. 纯函数判定 + 双层扣减 + 落流水（sealed + 模式匹配消费）
        // ⚠️ 白名单仍按<b>账号</b>匹配：t_prize_pool_item.white_list 里存的是运营手填的账号。
        //    这意味着白名单用户改名之后名单会失效 —— 已知缺口，等会员域收口后连同配置一起改成会员号。
        return switch (DrawEngine.draw(snapshot, memberName)) {
            case DrawResult.Hit(DrawPrizeSnapshot prize, DrawResult.HitSource source) ->
                    settle(form, memberName, traceId, snapshot, itemMap, prize, source, period);
            case DrawResult.NoStock(String candidateCode) -> {
                // 引擎不扣费也就不退费：上游若已扣过资产，按这个返回值自行退还
                saveLog(form, memberName, traceId, null, candidateCode, LOG_STATUS_NO_STOCK, "快照判定无库存");
                yield ResponseDTO.ok(DrawExecuteVO.ofMiss("手慢了，奖品已被抽完"));
            }
        };
    }

    /**
     * 中奖后发布派发事件：AFTER_COMMIT 投递到 consumer -> risk -> ledger 公共链路
     * traceId 作为 sourceBizId，配合 t_prize_log 唯一索引做跨系统防重
     */
    private void publishPrizeEvent(DrawExecuteForm form, String memberName, String traceId, String prizeCode) {
        PrizeConfig prizeConfig = prizeConfigService.getByActivityCodeAndPrizeCode(form.getActivityCode(), prizeCode);
        if (prizeConfig == null) {
            log.error("[抽奖派发] 奖品配置不存在，跳过派发: {}", prizeCode);
            return;
        }
        UserPrizeEvent event = UserPrizeEvent.builder()
                .sourceBizId(traceId)
                .activityCode(form.getActivityCode())
                .memberId(form.getMemberId())
                .memberName(memberName)
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
    private ResponseDTO<DrawExecuteVO> settle(DrawExecuteForm form, String memberName, String traceId,
                                              DrawPoolSnapshot snapshot,
                                              Map<Long, PrizePoolItem> itemMap,
                                              DrawPrizeSnapshot candidate, DrawResult.HitSource source,
                                              DrawPeriodResolver.Period period) {
        if (tryDeduct(form, itemMap, candidate, period)) {
            saveLog(form, memberName, traceId, candidate.prizeItemId(), candidate.prizeCode(), LOG_STATUS_HIT, source.name());
            publishPrizeEvent(form, memberName, traceId, candidate.prizeCode());
            return ResponseDTO.ok(DrawExecuteVO.ofHit(candidate.prizeItemId(), candidate.prizeCode(), source.name()));
        }

        // 候选奖项扣减失败（并发抢空/超单人限领）-> 降级兜底
        DrawPrizeSnapshot fallback = snapshot.fallbackPrize();
        if (fallback != null && fallback.prizeItemId() != candidate.prizeItemId()
                && tryDeduct(form, itemMap, fallback, period)) {
            saveLog(form, memberName, traceId, fallback.prizeItemId(), fallback.prizeCode(), LOG_STATUS_HIT,
                    DrawResult.HitSource.FALLBACK_DEGRADE.name());
            publishPrizeEvent(form, memberName, traceId, fallback.prizeCode());
            return ResponseDTO.ok(DrawExecuteVO.ofHit(fallback.prizeItemId(), fallback.prizeCode(),
                    DrawResult.HitSource.FALLBACK_DEGRADE.name()));
        }

        // 引擎不扣费也就不退费：上游若已扣过资产，按这个返回值自行退还
        saveLog(form, memberName, traceId, candidate.prizeItemId(), candidate.prizeCode(), LOG_STATUS_NO_STOCK, "预扣失败且兜底不可用");
        return ResponseDTO.ok(DrawExecuteVO.ofMiss("手慢了，奖品已被抽完"));
    }

    /**
     * 解析本次抽奖归属的单人限领周期桶。
     *
     * <p>两级短路，为的是不给热路径白加一次数据库往返：
     * <ol>
     *   <li>奖池的 reset_period 是 ACTIVITY / 空 / 脏值 —— 桶恒为 ALL，与时间无关；</li>
     *   <li>本池所有奖项都不限领（user_max_count 全是 -1）—— 计数 key 压根不会被读，
     *       桶叫什么都无所谓。</li>
     * </ol>
     * 两者任一成立就直接返回 ALL 桶，行为与「没有周期重置」时完全一致。
     *
     * <p>需要时钟时取的是<b>数据库</b>时间（铁律 9/10）：多实例部署下各节点 JVM 时钟未必一致，
     * 跨零点那一刻用 JVM 时间会出现「A 节点认为还是昨天、B 节点认为已是今天」，
     * 同一个用户在两个桶里各拿一次额度 —— 正好是限领要防的那件事。
     */
    private DrawPeriodResolver.Period resolvePeriod(PrizePoolConfig pool, Map<Long, PrizePoolItem> itemMap) {
        if (!DrawPeriodResolver.needsClock(pool.getResetPeriod())) {
            return new DrawPeriodResolver.Period(DrawPeriodResolver.BUCKET_ALL, DrawPeriodResolver.TTL_NONE);
        }
        boolean anyLimited = itemMap.values().stream()
                .anyMatch(item -> item.getUserMaxCount() != null && item.getUserMaxCount() != UNLIMITED);
        if (!anyLimited) {
            return new DrawPeriodResolver.Period(DrawPeriodResolver.BUCKET_ALL, DrawPeriodResolver.TTL_NONE);
        }
        return DrawPeriodResolver.resolve(pool.getResetPeriod(), prizePoolItemDao.selectDbNow());
    }

    /**
     * 双层扣减：Redis Lua 原子预扣扛并发；DB 条件更新做最终一致性兜底，DB 拒绝则补偿回滚 Redis
     */
    private boolean tryDeduct(DrawExecuteForm form, Map<Long, PrizePoolItem> itemMap, DrawPrizeSnapshot prize,
                              DrawPeriodResolver.Period period) {
        PrizePoolItem item = itemMap.get(prize.prizeItemId());
        int userMax = item == null || item.getUserMaxCount() == null ? UNLIMITED : item.getUserMaxCount();

        StockDeductResult redisResult = drawStockService.deduct(
                form.getActivityCode(), prize.prizeItemId(), form.getMemberId(), userMax, period);
        if (!redisResult.success()) {
            log.info("[抽奖预扣拒绝] memberId={}, prizeItemId={}, reason={}",
                    form.getMemberId(), prize.prizeItemId(), redisResult.message());
            return false;
        }

        try {
            int rows = prizePoolItemDao.increaseUsedStock(prize.prizeItemId());
            if (rows == 0) {
                // Redis 与 DB 不一致（如缓存被误预热），以 DB 为准并补偿
                drawStockService.rollback(form.getActivityCode(), prize.prizeItemId(), form.getMemberId(), period);
                log.warn("[抽奖DB兜底拒绝] prizeItemId={}, Redis已回滚", prize.prizeItemId());
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            drawStockService.rollback(form.getActivityCode(), prize.prizeItemId(), form.getMemberId(), period);
            throw e;
        }
    }

    private void saveLog(DrawExecuteForm form, String memberName, String traceId, Long prizeItemId, String prizeCode,
                         int status, String remark) {
        DrawPrizeLog logEntity = new DrawPrizeLog();
        logEntity.setTraceId(traceId);
        logEntity.setActivityCode(form.getActivityCode());
        logEntity.setPoolCode(form.getPoolCode());
        logEntity.setMemberId(form.getMemberId());
        // 抽奖流水是单据：账号只作展示快照落库
        logEntity.setMemberName(memberName);
        logEntity.setPrizeItemId(prizeItemId == null ? 0L : prizeItemId);
        logEntity.setPrizeCode(prizeCode);
        logEntity.setStatus(status);
        logEntity.setRemark(remark);
        drawPrizeLogDao.insert(logEntity);
    }

    /**
     * 库存快照：优先读 Redis；未预热则按 DB 回填
     * 回填必须走 SET NX 版本，否则缓存冷启动遇上高并发会把已发生的扣减覆盖掉（超发）
     */
    private int resolveRemainStock(String activityCode, PrizePoolItem item) {
        Integer cached = drawStockService.getRemainStock(activityCode, item.getId());
        if (cached != null) {
            return cached;
        }
        return drawStockService.warmStockIfAbsent(activityCode, item);
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
