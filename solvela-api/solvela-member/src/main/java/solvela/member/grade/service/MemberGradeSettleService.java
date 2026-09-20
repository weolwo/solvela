package solvela.member.grade.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import solvela.member.MemberGrade;
import solvela.member.MemberGrowth;
import solvela.member.MemberPeriodSummary;
import solvela.member.grade.GradeChangeType;
import solvela.member.grade.GradeSettleResult;
import solvela.member.grade.GrowthProperties;
import solvela.member.grade.dao.MemberGrowthDao;
import solvela.member.grade.dao.MemberPeriodSummaryDao;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 期末结算 —— <b>降级唯一会发生的地方</b>。
 *
 * <h3>三种到期，两个入口</h3>
 * <pre>
 *   周期到点，不在缓冲期
 *     ├─ 攒够了   → KEEP / UPGRADE，结清、清零、推进周期
 *     └─ 没攒够   → PROTECT_START：给一段缓冲期，等级先留着，成长值翻倍
 *                    （🔴 此时【不】结清、【不】清零、【不】推进周期）
 *
 *   缓冲期满
 *     ├─ 补够了   → PROTECT_KEPT，等级保住，结清
 *     └─ 还是不够 → PROTECT_FAILED，按成长值降级，结清
 * </pre>
 *
 * <h3>🔴 缓冲期是当前周期的【延长】，不是新周期</h3>
 * 进入缓冲时周期与累计值一概不动，用户继续往同一个计数器里加（×2），
 * 缓冲期满才一并结算、清零、推进。
 *
 * <p>这样「加速所得只归上一周期」是<b>自动成立</b>的 —— 缓冲期攒的全在旧计数器里，
 * 期满一起清零，一点都带不进新周期。换成「进缓冲就推进周期、加成 tag 回上一期」那种写法，
 * 就得靠跨周期求和才能判保级，而且会多出「这笔到底算哪期」这种只能靠约定维系的东西。
 *
 * <h3>🔴 一个周期，一行快照</h3>
 * {@code uk(member_id, period_no)} 是幂等键。整套动作在<b>一个事务</b>里，
 * 所以唯一键挡的是「两个调度节点同时捞到同一个人」，不是「自己跑了一半」。
 *
 * @author alaric
 * @date 2026-09-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberGradeSettleService {

    private static final DateTimeFormatter PERIOD_NO = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MemberGrowthDao memberGrowthDao;
    private final MemberPeriodSummaryDao memberPeriodSummaryDao;
    private final MemberGradeResolver memberGradeResolver;
    private final MemberGradeChangeService memberGradeChangeService;
    private final GrowthProperties growthProperties;

    /**
     * 结算一个会员。
     *
     * <p>🔴 {@code REQUIRES_NEW}：<b>一人一个事务</b>。一个人算错不该带着同一批的其他人回滚 ——
     * 这是分批 job 的前提（形状与 {@code MallOrderExpireJob.cancelExpired} 一致）。
     *
     * @return 这次的结果；{@code null} 表示「已被别人处理，本次跳过」
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String settle(MemberGrowth growth, LocalDateTime now) {
        List<MemberGrade> grades = memberGradeResolver.enabledGrades();
        long value = nullToZero(growth.getCurrentPeriodValue());
        int current = nullToZero(growth.getCurrentGrade()).intValue();
        int earned = memberGradeResolver.gradeOf(value, grades);

        boolean inProtect = growth.getProtectUntil() != null;
        return inProtect
                ? settleProtected(growth, now, grades, value, current, earned)
                : settleNormal(growth, now, grades, value, current, earned);
    }

    // ==================== 周期到点 ====================

    private String settleNormal(MemberGrowth growth, LocalDateTime now, List<MemberGrade> grades,
                                long value, int current, int earned) {
        if (earned < current) {
            /*
             * 没攒够 —— 但【先不降】，给一段缓冲期。
             *
             * 🔴 这里是整套机制的抓手：直接降级的话，用户是在「已经掉了」之后才知道；
             *    给缓冲期 + 双倍成长值，他是在「还够得着」的时候知道的。
             *    后者才可能把人拉回来，而这正是做等级体系要换的东西。
             */
            LocalDateTime protectUntil = now.plus(growthProperties.getProtectPeriod());
            int changed = memberGrowthDao.startProtect(
                    growth.getMemberId(), growth.getPeriodEnd(), protectUntil, current);
            if (changed == 0) {
                log.debug("【等级结算】进入缓冲时已被并发处理，跳过。memberId={}", growth.getMemberId());
                return null;
            }
            /*
             * ⚠️ 这一步【不写快照】：这个周期还没有结局。
             *    写了的话，缓冲期满还要再写一行，而 uk(member_id, period_no) 只允许一行 ——
             *    真写成两行就得给 period_no 加后缀，那等于承认「一个周期两个结局」。
             */
            log.info("【等级结算】进入保级缓冲。memberId={}, 保 {} 级, 成长值 {}, 缓冲至 {}",
                    growth.getMemberId(), current, value, protectUntil);
            return GradeSettleResult.PROTECT_START;
        }

        /*
         * 攒够了。earned > current 理论上走不到（升级是即时的），
         * 真走到说明中途漏判过 —— 或者运营把门槛调低了。两种都值得留一条痕。
         */
        String result = earned > current ? GradeSettleResult.UPGRADE : GradeSettleResult.KEEP;
        if (earned > current) {
            log.warn("【等级结算】期末才发现该升级，正常情况下升级是即时的。memberId={}, {} → {}",
                    growth.getMemberId(), current, earned);
            memberGradeChangeService.change(growth.getMemberId(), current, earned,
                    GradeChangeType.UPGRADE, value, "期末结算补升", null);
        }
        /*
         * ⚠️ 平级不写等级留痕。留痕回答的是「等级为什么变了」，而这里什么都没变；
         *    「这一期结局如何」由周期快照回答 —— 两张表各管一件事，不要互相灌水。
         */
        return closePeriod(growth, now, grades, value, current, earned, result);
    }

    // ==================== 缓冲期满 ====================

    private String settleProtected(MemberGrowth growth, LocalDateTime now, List<MemberGrade> grades,
                                   long value, int current, int earned) {
        int protectGrade = nullToZero(growth.getProtectGrade()).intValue();

        if (earned >= protectGrade) {
            /*
             * 保住了 —— 🔴 这一条【要】写等级留痕，哪怕等级没变。
             * 它是用户要看见的好消息（也是 GradeChangeType.KEEP 存在的唯一理由）：
             * 「你在缓冲期内补够了，白金保住了」。不留痕的话，用户只知道自己没掉，
             * 不知道系统给过他一次机会 —— 那段紧张感白费了。
             */
            memberGradeChangeService.change(growth.getMemberId(), current, current,
                    GradeChangeType.KEEP, value, "保级缓冲期内达标，等级保住", null);
            log.info("【等级结算】保级成功。memberId={}, 保住 {} 级, 成长值 {}",
                    growth.getMemberId(), current, value);
            return closePeriod(growth, now, grades, value, current, current,
                    GradeSettleResult.PROTECT_KEPT);
        }

        // 缓冲期都给了还是不够，这次真降
        memberGradeChangeService.change(growth.getMemberId(), current, earned,
                GradeChangeType.DOWNGRADE, value, "保级缓冲期满仍未达标", null);
        log.info("【等级结算】保级失败，降级。memberId={}, {} → {}, 成长值 {}",
                growth.getMemberId(), current, earned, value);
        return closePeriod(growth, now, grades, value, current, earned,
                GradeSettleResult.PROTECT_FAILED);
    }

    // ==================== 结清 ====================

    /**
     * 写快照 + 清零 + 推进周期。
     *
     * <p>🔴 顺序是<b>先写快照再清零</b>：快照要记的是清零前的那个数。
     * 反过来写的话，{@code final_growth_value} 永远是 0，而那是这张表存在的全部理由。
     */
    private String closePeriod(MemberGrowth growth, LocalDateTime now, List<MemberGrade> grades,
                               long value, int gradeBefore, int settledGrade, String result) {
        MemberGrade next = grades.stream()
                .filter(g -> g.getThreshold() != null && g.getThreshold() > value)
                .findFirst()
                .orElse(null);

        MemberPeriodSummary summary = new MemberPeriodSummary();
        summary.setMemberId(growth.getMemberId());
        summary.setPeriodNo(growth.getPeriodStart().format(PERIOD_NO));
        summary.setPeriodStart(growth.getPeriodStart());
        summary.setPeriodEnd(growth.getPeriodEnd());
        summary.setFinalGrowthValue(value);
        summary.setGradeBefore(gradeBefore);
        summary.setSettledGrade(settledGrade);
        summary.setSettleResult(result);
        summary.setNextGrade(next == null ? null : next.getGradeCode());
        // ⚠️ 门槛存【当时快照】：运营改过门槛之后，拿今天的配置回算会得出另一个答案，
        //    而客诉问的是「我当时差多少」
        summary.setNextThreshold(next == null ? null : next.getThreshold());
        summary.setProtectGrade(growth.getProtectGrade());
        summary.setSettledAt(now);
        memberPeriodSummaryDao.insert(summary);

        int changed = memberGrowthDao.closePeriod(growth.getMemberId(), growth.getPeriodEnd(),
                now, now.plus(growthProperties.getPeriod()));
        if (changed == 0) {
            /*
             * 影响 0 行 = 这一期已经被别的节点结过了。抛出去让整笔回滚（含刚插的快照、
             * 刚写的等级留痕），而不是留下「快照写了、周期没推进」那种半截状态 ——
             * 那会让下一轮再结算一次，撞唯一键之后就彻底卡住。
             */
            throw new IllegalStateException("周期已被并发结算，memberId=" + growth.getMemberId());
        }
        return result;
    }

    private static Long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private static Integer nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
