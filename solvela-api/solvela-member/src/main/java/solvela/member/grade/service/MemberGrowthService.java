package solvela.member.grade.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solvela.member.MemberGrowth;
import solvela.member.MemberGrowthLog;
import solvela.member.grade.GrowthProperties;
import solvela.member.grade.GradeChangeType;
import solvela.member.grade.dao.MemberGrowthDao;
import solvela.member.grade.dao.MemberGrowthLogDao;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 成长值累加器。
 *
 * <h3>它只做一件事：把一笔成长值记进去</h3>
 * 升级判定、降级结算、保级缓冲都<b>不在这里</b> —— 那些是阶段 2 / 4 的事，
 * 各自有各自的入口。本类做完的标志是：流水落了、周期累计对了。
 *
 * <h3>🔴 幂等靠唯一键，不靠先查后写</h3>
 * 上游打点是<b>至少一次</b>的（对账 job 会重推漏投的）。
 * 先查一次「这个 bizId 记过没有」再写，中间有窗口 —— 两次重推撞在一起会各写一条。
 * 唯一键 {@code (source, biz_id)} 是唯一挡得住的东西，撞了就当成「已经记过」。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberGrowthService {

    private static final DateTimeFormatter PERIOD_TAG = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MemberGrowthDao memberGrowthDao;
    private final MemberGrowthLogDao memberGrowthLogDao;
    private final MemberGradeResolver memberGradeResolver;
    private final GrowthProperties growthProperties;
    private final MemberGradeChangeService memberGradeChangeService;

    /**
     * 记一笔成长值。
     *
     * @param memberId  会员号
     * @param baseValue 倍率<b>之前</b>的基数
     * @param source    来源（{@code SCORE_EARNED} …）
     * @param bizType   上游业务类型，只落流水供排查，判「算不算」在调用方
     * @param bizId     上游业务单号，<b>幂等键</b>
     * @param remark    C 端展示摘要
     * @return 真的记进去了返回 true；重复投递返回 false
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean accrue(Long memberId, long baseValue, String source,
                          String bizType, String bizId, String remark) {
        if (memberId == null || baseValue <= 0 || bizId == null || bizId.isBlank()) {
            log.warn("【成长值】参数不完整，本次不记。memberId={}, base={}, bizId={}",
                    memberId, baseValue, bizId);
            return false;
        }

        MemberGrowth growth = loadOrInit(memberId);

        /*
         * 倍率：保级缓冲期 2 倍。
         *
         * 🔴 倍率只作用在成长值上，【绝不】回头去多发积分 —— 积分是负债。
         *    这里要给的只是一个够得着的目标。
         */
        int multiplier = inProtectPeriod(growth) ? growthProperties.getProtectMultiplier() : 1;
        long delta = baseValue * multiplier;

        /*
         * period_tag 永远取【当前 period_start】，缓冲期也一样。
         *
         * 🔴 2026-09-20 订正：阶段 1 这里写的是「缓冲期减一个周期」，
         *    那是基于「进入缓冲时周期会推进」的假设 —— 阶段 4 写结算时把口径定反了：
         *    <b>缓冲期是当前周期的延长，周期不推进</b>（见 GrowthProperties.protectPeriod）。
         *    照旧写法会把缓冲期的加成 tag 到【上上个周期】去，而那个周期早就结算完了，
         *    对账时表现为「有一笔流水找不到对应的周期快照」。
         *
         * 「加速所得只归上一周期」这条规则没变，只是实现方式变了：
         * 缓冲期攒的全在旧计数器里，期满一起清零 —— 天然带不进新周期，
         * 不需要靠 tag 去表达归属。
         */
        String periodTag = tagOf(growth.getPeriodStart());

        MemberGrowthLog logRow = new MemberGrowthLog();
        logRow.setMemberId(memberId);
        logRow.setDelta(delta);
        logRow.setBaseValue(baseValue);
        logRow.setMultiplier(multiplier);
        logRow.setSource(source);
        logRow.setBizType(bizType);
        logRow.setBizId(bizId);
        logRow.setPeriodTag(periodTag);
        logRow.setRemark(remark);
        // 累加之后的值，先按当前快照算；真实值以 addValue 之后为准，
        // 这里存的是对账锚点，差一点点不影响它的用途
        logRow.setAfterPeriodValue(nullToZero(growth.getCurrentPeriodValue()) + delta);

        try {
            memberGrowthLogDao.insert(logRow);
        } catch (DuplicateKeyException e) {
            /*
             * 撞唯一键 = 这一笔已经记过了。
             *
             * 🔴 这不是错误，是【打点至少一次】的正常结果：对账 job 每轮都会
             *    把窗口内的单子重推一遍，绝大多数都会撞在这里。
             *    当成异常抛出去的话，对账任务每跑一次就刷一屏 error。
             */
            log.debug("【成长值】重复投递，已忽略。source={}, bizId={}", source, bizId);
            return false;
        }

        // 🔴 原子累加，不是读出来加完再写回 —— 并发入账会丢更新，
        //    而丢的那一次在流水里是有的，于是明细和总数对不上
        int changed = memberGrowthDao.addValue(memberId, delta);
        if (changed == 0) {
            // loadOrInit 刚建过，走不到。真走到了说明有人并发删了行 —— 让事务回滚，
            // 流水也跟着回滚，下一轮对账会把它重推回来
            throw new IllegalStateException("成长值累加失败，会员行不存在：" + memberId);
        }

        log.info("【成长值】+{}（基数 {} × {}倍），memberId={}, source={}, bizId={}",
                delta, baseValue, multiplier, memberId, source, bizId);

        // 阶段 2：入账完当场判一次级。升级是即时的，用户攒够了就该马上看见
        evaluateUpgrade(memberId);
        return true;
    }

    /**
     * 入账之后判一次级，够门槛就当场升上去。
     *
     * <h3>🔴 只升不降</h3>
     * 入账只会让成长值变大，所以这里<b>永远不该发生降级</b>。
     * 但「算出来比当前低」是真的会出现的，而且有三种正当来源：
     * <ol>
     *   <li>运营调高了门槛 —— 存量用户的成长值没变，档位变了；</li>
     *   <li>人工调级把某人提到了他还没攒够的等级；</li>
     *   <li>保级缓冲期内等级取 {@code protect_target}，本来就高于 f(成长值)。</li>
     * </ol>
     * 三种情况下「什么都不做」都是对的。<b>真正致命的是反过来</b>：
     * 如果这里照着算出来的值改，运营调高一次门槛就会在下一次入账时
     * 把所有存量用户<b>静默降级</b> —— 没有人点过「降级」，也不会有任何报错。
     * 降级只属于期末结算那一条路（阶段 4）。
     *
     * <h3>⚠️ 并发撞车时本次跳过，不重试</h3>
     * 两笔入账同时到达、又恰好一起跨过两档时，后到的那条会因为
     * 「等级已经不是我读到的那个」而更新 0 行，于是这一次少升一级。
     *
     * <p>不加重试是因为它<b>会自愈</b>：判级不以「这次跨没跨门槛」为条件，
     * 下一笔成长值进来时照样重算一次，那时就补上了。
     * 为此在热路径上加 {@code FOR UPDATE}，等于让同一个会员的所有入账排队，
     * 换来的只是「升级早一笔」。
     */
    private void evaluateUpgrade(Long memberId) {
        /*
         * 🔴 这里必须重新读，不能用 accrue 开头那个 growth 快照。
         *    period_value 是原子自增上去的，快照 + delta 在并发下是错的 ——
         *    那正是 addValue 存在的理由，判级时再用一次就把它抵消了。
         */
        MemberGrowth fresh = memberGrowthDao.selectById(memberId);
        if (fresh == null) {
            // addValue 刚刚影响了 1 行，走不到
            log.warn("【会员等级】判级时读不到成长值行，跳过。memberId={}", memberId);
            return;
        }
        long periodValue = nullToZero(fresh.getCurrentPeriodValue());
        int current = fresh.getCurrentGrade() == null ? 0 : fresh.getCurrentGrade();
        int target = memberGradeResolver.gradeOf(periodValue);

        if (target <= current) {
            log.debug("【会员等级】等级未变。memberId={}, 当前={}, 按成长值应为={}, periodValue={}",
                    memberId, current, target, periodValue);
            return;
        }
        memberGradeChangeService.change(memberId, current, target,
                GradeChangeType.UPGRADE, periodValue, null, null);
    }

    /**
     * 取这个会员的成长值行，没有就<b>按需创建</b>。
     *
     * <h3>🔴 存量会员的周期从「第一次入账」起算，不从注册日</h3>
     * 库里已经有 9000+ 会员，绝大多数注册于很久以前。按注册日算的话，
     * 他第一次拿到成长值时<b>就已经在周期末尾了</b> —— 甚至是过期的，
     * 一进来就面临降级判定，而他根本没机会攒。
     *
     * <p>所以周期从这一刻起算。代价是「入会日」对存量会员不是真的入会日，
     * 但那对用户是<b>更宽松</b>的一边，而且他看到的「本期截止」是真实可达的。
     */
    public MemberGrowth loadOrInit(Long memberId) {
        MemberGrowth growth = memberGrowthDao.selectById(memberId);
        if (growth != null) {
            return growth;
        }
        LocalDateTime now = memberGrowthDao.selectDbNow();
        MemberGrowth fresh = new MemberGrowth();
        fresh.setMemberId(memberId);
        fresh.setCurrentGrade(0);
        fresh.setGradeSince(now);
        fresh.setPeriodStart(now);
        fresh.setPeriodEnd(now.plus(growthProperties.getPeriod()));
        fresh.setCurrentPeriodValue(0L);
        fresh.setTotalValue(0L);
        try {
            memberGrowthDao.insert(fresh);
            return fresh;
        } catch (DuplicateKeyException e) {
            // 两笔入账同时到达、都发现没有行。谁建成了都对，重查一次即可
            log.debug("【成长值】并发初始化，已由另一方建成。memberId={}", memberId);
            return memberGrowthDao.selectById(memberId);
        }
    }

    /** 当前等级。查不到会员返回 {@code null} —— 「不知道」和「0 级」是两件事，见 §6.1 */
    public Integer currentGrade(Long memberId) {
        if (memberId == null) {
            return null;
        }
        MemberGrowth growth = memberGrowthDao.selectById(memberId);
        if (growth == null) {
            /*
             * 没有成长值行 ≠ 查不到会员：一个从没拿过积分的真实会员就是这样。
             * 他确定是 0 级，所以这里返回 0 而不是 null。
             *
             * 真正的「不知道」是会员号根本不存在 —— 那由调用方的
             * MemberService.requireExists 之类去判，不该在这里猜。
             */
            return 0;
        }
        // ⚠️ 缓冲期内直接取存的 grade（= protect_target），不重算 f(periodValue)
        return growth.getCurrentGrade();
    }

    /** 在保级缓冲期内吗 */
    private boolean inProtectPeriod(MemberGrowth growth) {
        return growth.getProtectUntil() != null
                && growth.getProtectUntil().isAfter(memberGrowthDao.selectDbNow());
    }

    private static String tagOf(LocalDateTime periodStart) {
        return periodStart.format(PERIOD_TAG);
    }

    private static long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
