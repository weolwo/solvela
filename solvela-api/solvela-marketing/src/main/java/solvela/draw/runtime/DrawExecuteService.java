package solvela.draw.runtime;

import solvela.base.util.SolvelaRandomUtil;
import solvela.enums.ActivityTypeEnum;
import solvela.marketing.api.DrawRejectReason;
import solvela.enums.DrawResultEnum;
import solvela.enums.PrizePoolStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import solvela.base.json.JsonUtils;
import tools.jackson.core.type.TypeReference;
import solvela.draw.drawlog.dao.DrawPrizeLogDao;
import solvela.draw.DrawPrizeLog;
import solvela.draw.engine.DrawEngine;
import solvela.draw.engine.DrawPoolSnapshot;
import solvela.draw.engine.DrawPrizeSnapshot;
import solvela.draw.engine.DrawResult;
import solvela.draw.engine.DrawSlot;
import solvela.draw.DrawConfig;
import solvela.draw.PrizePoolConfig;
import solvela.draw.poolconfig.manager.PrizePoolConfigManager;
import solvela.draw.poolitem.dao.PrizePoolItemDao;
import solvela.draw.PrizePoolItem;
import solvela.draw.poolitem.manager.PrizePoolItemManager;
import solvela.draw.PoolPrizeMapping;
import solvela.draw.prizemapping.manager.PoolPrizeMappingManager;
import solvela.draw.drawconfig.service.DrawConfigService;
import solvela.draw.runtime.domain.DrawExecuteCommand;
import solvela.draw.runtime.domain.DrawExecuteResult;
import solvela.draw.runtime.domain.DrawRecord;
import solvela.event.UserPrizeEvent;
import solvela.member.service.MemberService;
import solvela.prize.PrizeConfig;
import solvela.prize.prizeconfig.service.PrizeConfigService;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import solvela.dispatch.outbox.PrizeEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final DrawConfigService drawConfigService;
    private final PoolPrizeMappingManager poolPrizeMappingManager;
    private final PrizePoolItemManager prizePoolItemManager;
    private final PrizePoolItemDao prizePoolItemDao;
    private final DrawPrizeLogDao drawPrizeLogDao;
    private final DrawStockService drawStockService;
    private final RedissonClient redissonClient;
    private final PrizeConfigService prizeConfigService;
    private final PrizeEventPublisher prizeEventPublisher;
    /**
     * 会员号 -> 账号。一次抽奖只查一次，结果沿调用链传下去：
     * 白名单判定、流水快照、派发事件三处都要用，各查一次就是三次往返。
     */
    private final MemberService memberService;
    /**
     * {@code t_prize_pool_item.user_max_count} 的「不限领」哨兵值。
     *
     * <p>⚠️ 与 {@link DrawPrizeSnapshot#UNLIMITED}（库存不限量）同值但<b>不是一个概念</b>：
     * 那个说的是「这个奖发不完」，这个说的是「一个人能拿几份不设上限」。
     */
    private static final int USER_LIMIT_UNLIMITED = -1;

    /**
     * 防刷限流：单用户单活动每秒最多 2 次；限流器 key 一小时无活动自动过期
     */
    private static final long RATE_LIMIT_PER_INTERVAL = 2;
    private static final long RATE_LIMIT_INTERVAL_SECONDS = 1;
    private static final Duration RATE_LIMITER_TTL = Duration.ofHours(1);
    /**
     * 幂等标记有效期：覆盖常见网络重试窗口
     */
    private static final Duration REQUEST_DEDUP_TTL = Duration.ofMinutes(10);

    /** 幂等键的占位值：已抢到锁但结果还没落定。与真实结果（JSON 数组）区分得开 */
    private static final String IN_FLIGHT = "IN_FLIGHT";

    /**
     * 事务边界说明：DB 写入（资格扣减/流水/库存兜底）同一事务；
     * UserPrizeEvent 经 @TransactionalEventListener(AFTER_COMMIT) 在提交后才真正派发，天然防「事务回滚但奖已发」
     */
    @Transactional(rollbackFor = Exception.class)
    public DrawExecuteResult execute(DrawExecuteCommand form) {
        if (form.times() <= 0) {
            // 不是业务规则，是退化输入。静默返回「抽了 0 次」会让上游以为成功了，
            // 而它多半是上游把次数算错了
            return new DrawExecuteResult.Rejected(DrawRejectReason.INVALID_TIMES);
        }

        // 1. 幂等：命中就【回放第一次的结果】，而不是告诉调用方「你重复了」。
        //    reject 的契约是「压根没抽、请退还」，而重试时第一次真的抽了 ——
        //    用 reject 表达重试，会让上游把已中奖那一次的资产退回去。连抽后这是 N 份奖的事
        boolean idempotent = form.requestId() != null && !form.requestId().isBlank();
        String resultKey = idempotent ? DrawCacheKey.request(form.requestId()) : null;
        if (idempotent) {
            boolean first = redissonClient.getBucket(resultKey, StringCodec.INSTANCE)
                    .setIfAbsent(IN_FLIGHT, REQUEST_DEDUP_TTL);
            if (!first) {
                DrawExecuteResult replayed = replay(resultKey);
                if (replayed != null) {
                    return replayed;
                }
                // 拿到了锁标记但还没有结果 —— 上一次【正在处理中】，这是真正的并发重复提交。
                // 这时说「重复请求」是准确的：那一次还没落定，没有结果可回放
                return new DrawExecuteResult.Rejected(DrawRejectReason.DUPLICATE_REQUEST);
            }
        }

        // 2. 防刷限流（单用户单活动）。连抽只消耗一个令牌 ——
        //    它限的是【请求频率】，防的是脚本刷接口；能抽多少由资产与单人限领决定
        // 🔴 限流 key 用会员号：账号可改，改完就是一个全新的 key，限流当场归零
        RRateLimiter limiter = redissonClient.getRateLimiter(
                DrawCacheKey.rateLimit(form.activityCode(), form.memberId()));
        if (limiter.trySetRate(RateType.OVERALL, RATE_LIMIT_PER_INTERVAL, RATE_LIMIT_INTERVAL_SECONDS, RateIntervalUnit.SECONDS)) {
            limiter.expire(RATE_LIMITER_TTL);
        }
        if (!limiter.tryAcquire()) {
            return reject(resultKey, DrawRejectReason.TOO_FREQUENT);
        }

        // 会员号必须真实存在；顺带把账号取回来，后面白名单判定、流水快照、派发事件都要用。
        // 一次请求只查这一次，不在每个用到名字的地方各查一次。
        // ⚠️ 刻意放在限流【之后】：这是一次数据库往返，放在限流之前等于防刷没保护住它
        String memberName = memberService.requireMemberName(form.memberId());

        // 3. 奖池校验
        PrizePoolConfig pool = prizePoolConfigManager.lambdaQuery()
                .eq(PrizePoolConfig::getPoolCode, form.poolCode()).one();
        if (pool == null || !form.activityCode().equals(pool.getActivityCode())) {
            return reject(resultKey, DrawRejectReason.POOL_NOT_FOUND);
        }
        if (pool.getStatus() != PrizePoolStatusEnum.OPEN) {
            return reject(resultKey, DrawRejectReason.POOL_CLOSED);
        }

        // 4. 组装快照（配置读 DB，库存读 Redis；缓存未预热则回源并预热）。
        //    整批只组装一次：连抽 N 次不该打 N 轮配置查询
        List<PoolPrizeMapping> mappings = poolPrizeMappingManager.lambdaQuery()
                .eq(PoolPrizeMapping::getPoolCode, form.poolCode())
                .orderByAsc(PoolPrizeMapping::getSortWeight).list();
        if (mappings.isEmpty()) {
            return reject(resultKey, DrawRejectReason.POOL_NO_PRIZE);
        }
        List<Long> itemIds = mappings.stream().map(PoolPrizeMapping::getPrizeItemId).toList();
        Map<Long, PrizePoolItem> itemMap = prizePoolItemManager.listByIds(itemIds).stream()
                .collect(Collectors.toMap(PrizePoolItem::getId, Function.identity()));

        List<DrawSlot> slots = new ArrayList<>(mappings.size());
        // 顺序已由上面的 orderByAsc(sortWeight) 保证，这里不再排第二遍
        for (PoolPrizeMapping mapping : mappings) {
            PrizePoolItem item = itemMap.get(mapping.getPrizeItemId());
            if (item == null) {
                // 配置坏了要告警。itemId 只进日志不进返回值 —— 它是奖池内部主键，
                // 而这个返回值最终会走到 C 端；运维要的那个数在日志里查得到
                log.error("[抽奖] 奖池配置异常：奖项已被删除, poolCode: {}, itemId: {}",
                        form.poolCode(), mapping.getPrizeItemId());
                return reject(resultKey, DrawRejectReason.POOL_BROKEN);
            }
            DrawPrizeSnapshot prize = new DrawPrizeSnapshot(
                    item.getId(),
                    item.getPrizeCode(),
                    Boolean.TRUE.equals(mapping.getIsFallback()),
                    resolveRemainStock(form.activityCode(), item),
                    parseWhiteList(item.getWhiteList()));
            // 百分比 -> ppm 的【唯一】转换点：过了这一行，运行态就不再有小数
            slots.add(DrawSlot.ofPercent(prize, mapping.getProbability()));
        }
        DrawPoolSnapshot snapshot = DrawPoolSnapshot.of(form.poolCode(), slots);

        /*
         * 5. 解析本次抽奖归属的限领周期桶。
         *
         * 只有「奖池配了按天/周/月重置」且「确实存在限领的奖项」时才去取数据库时钟 ——
         * 抽奖是热路径，一次额外往返能省则省。两个条件任一不成立，桶都恒为 ALL。
         */
        DrawPeriodResolver.Period period = resolvePeriod(pool, itemMap);

        /*
         * 6. 逐次抽奖。
         *
         * 🔴 快照必须在批内递减：remainStock 是抽之前的值。N 次全拿同一份判定的话，
         *    只剩 1 个的奖会被判定 N 次「有货」，而实际只有第一次扣得动，
         *    后面全部走 fallback —— 用户看到的是「N-1 连保底」。
         *
         * 判定用引擎（纯函数），真扣用 settle，然后按【真实结果】更新快照再进下一次 ——
         * 而不是让引擎一次判完 N 次，那样它的库存视图会与真实扣减结果漂移。
         *
         * ⚠️ 白名单是「必中且每次判定都优先命中」，所以连抽会让白名单用户
         *    连拿 N 份同一个奖（直到该奖库存耗尽）。这是连抽才暴露出来的语义 ——
         *    现状如此，要改成「一批只必中一次」得先定产品口径。
         */
        List<DrawRecord> records = new ArrayList<>(form.times());
        for (int i = 0; i < form.times(); i++) {
            String sourceBizId = sourceBizId(form, i);
            DrawRecord record = switch (DrawEngine.draw(snapshot, memberName)) {
                case DrawResult.Hit(DrawPrizeSnapshot prize, DrawResult.HitSource source) ->
                        settle(form, memberName, sourceBizId, snapshot, itemMap, prize, source, period);
                case DrawResult.NoStock(String candidateCode) -> {
                    saveLog(form, memberName, sourceBizId, null, candidateCode,
                            DrawResultEnum.NO_STOCK, "快照判定无库存");
                    yield DrawRecord.miss(sourceBizId);
                }
            };
            records.add(record);
            if (record.hit()) {
                snapshot = snapshot.withStockConsumed(record.prizeItemId());
            }
        }

        DrawExecuteResult result = new DrawExecuteResult.Executed(records);
        remember(resultKey, result);
        return result;
    }

    /**
     * 本次抽奖的业务单号。
     *
     * <h3>🔴 每一次必须各不相同</h3>
     * 它落 {@code t_draw_prize_log.trace_id}，并作为 {@code UserPrizeEvent.sourceBizId}
     * 一路传到发奖侧写进 {@code t_prize_log.external_biz_no} —— 那一列有唯一索引
     * {@code uk_external_biz}。N 连抽若共用一个单号，发奖侧只会落第一条，
     * <b>后 N-1 个奖撞唯一约束静默丢失</b>。
     *
     * <p>带 requestId 前缀是为了让重投仍然幂等：同一批重投产生同样的 N 个单号。
     * 没有 requestId 时退化成随机串（后台联调、定时任务那类调用方）。
     * 单抽时不加后缀 —— 保持与改动之前完全一致的单号形态。
     */
    private static String sourceBizId(DrawExecuteCommand form, int index) {
        String batch = form.requestId() != null && !form.requestId().isBlank()
                ? form.requestId() : SolvelaRandomUtil.randomString();
        return form.times() == 1 ? batch : batch + "#" + index;
    }

    /** 拒绝时把幂等锁标记删掉：这一批没抽，调用方改正之后应当能重试同一个 requestId */
    private DrawExecuteResult reject(String resultKey, DrawRejectReason reason) {
        if (resultKey != null) {
            redissonClient.getBucket(resultKey, StringCodec.INSTANCE).delete();
        }
        return new DrawExecuteResult.Rejected(reason);
    }

    /**
     * 回放第一次的结果。拿不到（还在处理中、或反序列化失败）返回 null，由调用方决定怎么办。
     *
     * <p>反序列化失败不抛：那会让一次网络重试变成 500，而重试本来是最不该炸的路径。
     */
    private DrawExecuteResult replay(String resultKey) {
        String cached = redissonClient.<String>getBucket(resultKey, StringCodec.INSTANCE).get();
        if (cached == null || IN_FLIGHT.equals(cached)) {
            return null;
        }
        try {
            List<DrawRecord> records = JsonUtils.parseType(cached, new TypeReference<List<DrawRecord>>() {
            });
            return records == null ? null : new DrawExecuteResult.Executed(records);
        } catch (RuntimeException e) {
            log.error("[抽奖幂等] 结果回放失败，key={}", resultKey, e);
            return null;
        }
    }

    /**
     * 把结果写进幂等键，供重试回放。
     *
     * <p>⚠️ 写失败只记日志不抛：奖已经发了，为一次缓存写入失败去回滚整批抽奖更糟。
     * 代价是那一批重试会拿到 DUPLICATE_REQUEST 而不是结果 —— 少发总比重复发好。
     */
    private void remember(String resultKey, DrawExecuteResult result) {
        if (resultKey == null || !(result instanceof DrawExecuteResult.Executed(List<DrawRecord> records))) {
            return;
        }
        try {
            redissonClient.getBucket(resultKey, StringCodec.INSTANCE)
                    .set(JsonUtils.toJson(records), REQUEST_DEDUP_TTL);
        } catch (RuntimeException e) {
            log.error("[抽奖幂等] 结果写入失败，该批重试将拿到 DUPLICATE_REQUEST，key={}", resultKey, e);
        }
    }

    /**
     * 中奖后发布派发事件：AFTER_COMMIT 投递到 consumer -> risk -> ledger 公共链路
     * 每次抽奖各自的 sourceBizId，配合 t_prize_log.uk_external_biz 做跨系统防重 ——
     * 连抽共用一个单号的话，发奖侧只会落第一条，后面的全被唯一约束吃掉
     */
    private void publishPrizeEvent(DrawExecuteCommand form, String memberName, String sourceBizId, String prizeCode) {
        PrizeConfig prizeConfig = prizeConfigService.getByActivityCodeAndPrizeCode(form.activityCode(), prizeCode);
        if (prizeConfig == null) {
            log.error("[抽奖派发] 奖品配置不存在，跳过派发: {}", prizeCode);
            return;
        }
        UserPrizeEvent event = UserPrizeEvent.builder()
                .sourceBizId(sourceBizId)
                .activityCode(form.activityCode())
                // 抽奖发出来的奖，类型必然是 DRAW。派发方据它归类提案来源，
                // 不填的话那边只能降级成 MANUAL —— 对账时这批奖就归错类了
                .activityType(ActivityTypeEnum.DRAW.getValue())
                .memberId(form.memberId())
                .memberName(memberName)
                .prizeCode(prizeCode)
                .prizeType(prizeConfig.getPrizeType())
                .prizeValue(prizeConfig.getPrizeValue() == null ? null : prizeConfig.getPrizeValue().toPlainString())
                .prizeName(prizeConfig.getPrizeName())
                .build();
        prizeEventPublisher.publish(event);
    }

    /**
     * 结算一次命中：Lua 预扣 -> DB 条件更新兜底 -> 失败降级兜底奖项 -> 落流水。
     *
     * <p>返回的 {@link DrawRecord} 就是这一次的最终结论：真扣下来了才算 hit，
     * 兜底也扣不动就是 miss。<b>不回滚前面几次</b>（各抽各的）。
     */
    private DrawRecord settle(DrawExecuteCommand form, String memberName, String sourceBizId,
                              DrawPoolSnapshot snapshot,
                              Map<Long, PrizePoolItem> itemMap,
                              DrawPrizeSnapshot candidate, DrawResult.HitSource source,
                              DrawPeriodResolver.Period period) {
        if (tryDeduct(form, itemMap, candidate, period)) {
            saveLog(form, memberName, sourceBizId, candidate.prizeItemId(), candidate.prizeCode(),
                    DrawResultEnum.HIT, source.name());
            publishPrizeEvent(form, memberName, sourceBizId, candidate.prizeCode());
            return DrawRecord.hit(sourceBizId, candidate.prizeItemId(), candidate.prizeCode(), source);
        }

        // 候选奖项扣减失败（并发抢空/超单人限领）-> 降级兜底
        DrawPrizeSnapshot fallback = snapshot.fallbackPrize();
        if (fallback != null && fallback.prizeItemId() != candidate.prizeItemId()
                && tryDeduct(form, itemMap, fallback, period)) {
            saveLog(form, memberName, sourceBizId, fallback.prizeItemId(), fallback.prizeCode(),
                    DrawResultEnum.HIT, DrawResult.HitSource.FALLBACK_DEGRADE.name());
            publishPrizeEvent(form, memberName, sourceBizId, fallback.prizeCode());
            return DrawRecord.hit(sourceBizId, fallback.prizeItemId(), fallback.prizeCode(),
                    DrawResult.HitSource.FALLBACK_DEGRADE);
        }

        // 引擎不扣费也就不退费：上游若已扣过资产，按这条记录自行退还
        saveLog(form, memberName, sourceBizId, candidate.prizeItemId(), candidate.prizeCode(),
                DrawResultEnum.NO_STOCK, "预扣失败且兜底不可用");
        return DrawRecord.miss(sourceBizId);
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
        // 重置周期是玩法级配置，住在 t_draw_config 上 —— 它管的是「这套抽奖的限领多久清一次」，
        // 不是某个奖池自己的事。取不到配置时 resetPeriod 为 null，下面按「不重置」处理
        DrawConfig drawConfig = drawConfigService.getByPool(pool);
        String resetPeriod = drawConfig == null ? null : drawConfig.getResetPeriod();

        if (!DrawPeriodResolver.needsClock(resetPeriod)) {
            return new DrawPeriodResolver.Period(DrawPeriodResolver.BUCKET_ALL, DrawPeriodResolver.TTL_NONE);
        }
        boolean anyLimited = itemMap.values().stream()
                .anyMatch(item -> item.getUserMaxCount() != null && item.getUserMaxCount() != USER_LIMIT_UNLIMITED);
        if (!anyLimited) {
            return new DrawPeriodResolver.Period(DrawPeriodResolver.BUCKET_ALL, DrawPeriodResolver.TTL_NONE);
        }
        return DrawPeriodResolver.resolve(resetPeriod, prizePoolItemDao.selectDbNow());
    }

    /**
     * 双层扣减：Redis Lua 原子预扣扛并发；DB 条件更新做最终一致性兜底，DB 拒绝则补偿回滚 Redis
     */
    private boolean tryDeduct(DrawExecuteCommand form, Map<Long, PrizePoolItem> itemMap, DrawPrizeSnapshot prize,
                              DrawPeriodResolver.Period period) {
        PrizePoolItem item = itemMap.get(prize.prizeItemId());
        int userMax = item == null || item.getUserMaxCount() == null ? USER_LIMIT_UNLIMITED : item.getUserMaxCount();

        StockDeductResult redisResult = drawStockService.deduct(
                form.activityCode(), prize.prizeItemId(), form.memberId(), userMax, period);
        if (!redisResult.success()) {
            log.info("[抽奖预扣拒绝] memberId={}, prizeItemId={}, reason={}",
                    form.memberId(), prize.prizeItemId(), redisResult.message());
            return false;
        }
        // 🔴 预扣已经落到 Redis 了，从这一刻起它必须跟着事务走
        registerRedisRollbackOnFailure(form, prize.prizeItemId(), period);

        try {
            int rows = prizePoolItemDao.increaseUsedStock(prize.prizeItemId());
            if (rows == 0) {
                // Redis 与 DB 不一致（如缓存被误预热），以 DB 为准并补偿
                drawStockService.rollback(form.activityCode(), prize.prizeItemId(), form.memberId(), period);
                log.warn("[抽奖DB兜底拒绝] prizeItemId={}, Redis已回滚", prize.prizeItemId());
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            drawStockService.rollback(form.activityCode(), prize.prizeItemId(), form.memberId(), period);
            throw e;
        }
    }

    /**
     * 事务没提交时把这一次 Redis 预扣补偿回去。
     *
     * <h3>🔴 补的是一个真实缺口，不是防御性代码</h3>
     * Redis 预扣不参与数据库事务。{@link #tryDeduct} 成功之后，本次抽奖还要落流水
     * （{@link #saveLog} 的 insert）、整批还要继续抽 —— 其中任何一步抛异常，
     * {@code @Transactional} 会回滚掉 DB 的 {@code increaseUsedStock}，
     * 但 <b>Redis 里那一份预扣留在原地</b>。
     *
     * <p>而运行态「还能不能抽到」看的正是 Redis（{@link #resolveRemainStock} 优先读它，
     * DB 只在未预热时兜底）。结果就是奖品提前抽不到 —— 正是库存看板
     * （{@code PrizeItemStockService}）标成 danger 的 {@code STOCK_DRIFT}
     * 「Redis 偏少会让奖品提前抽不到」那一档。看板能发现，但没有自动修复。
     *
     * <p>连抽把这个窗口放大了 N 倍：一批 10 次，第 8 次抛异常，前 7 次的预扣全部留下。
     *
     * <p>⚠️ 用 {@code afterCompletion} 而不是 {@code afterRollback}：前者在提交与回滚
     * 两种收尾下都会被调到，我们据 status 判断，漏不掉「既没提交也没回滚」那类异常收尾。
     * 补偿本身失败只记日志 —— 那时事务已经结束，抛出去没有任何人能处理。
     */
    private void registerRedisRollbackOnFailure(DrawExecuteCommand form, long prizeItemId,
                                                DrawPeriodResolver.Period period) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 没有事务上下文（不该发生，本方法在 @Transactional 之内）。
            // 静默跳过等于把缺口留着，所以至少要留下痕迹
            log.warn("[抽奖预扣] 不在事务上下文中，Redis 预扣无法挂载回滚补偿, prizeItemId={}", prizeItemId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    return;
                }
                try {
                    drawStockService.rollback(form.activityCode(), prizeItemId, form.memberId(), period);
                    log.warn("[抽奖预扣补偿] 事务未提交，已回滚 Redis 预扣, prizeItemId={}, status={}",
                            prizeItemId, status);
                } catch (RuntimeException e) {
                    // 补偿失败 = 库存漂移。库存看板会把它标成 STOCK_DRIFT，运维据此人工校准
                    log.error("[抽奖预扣补偿失败] 库存将出现漂移（Redis 偏少），prizeItemId={}", prizeItemId, e);
                }
            }
        });
    }

    private void saveLog(DrawExecuteCommand form, String memberName, String sourceBizId, Long prizeItemId, String prizeCode,
                         DrawResultEnum status, String remark) {
        DrawPrizeLog logEntity = new DrawPrizeLog();
        // 列名叫 trace_id，存的是本次抽奖的业务单号（每次各不相同）——
        // 与 solvela.app.web.Trace 那个跨服务链路 id【不是一回事】，历史命名
        logEntity.setTraceId(sourceBizId);
        logEntity.setActivityCode(form.activityCode());
        logEntity.setPoolCode(form.poolCode());
        logEntity.setMemberId(form.memberId());
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
