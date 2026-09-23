package solvela.member.entitlement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import solvela.base.util.SolvelaCodeUtil;
import solvela.enums.EnableStatusEnum;
import solvela.member.GradeEntitlement;
import solvela.member.GradeEntitlementGrant;
import solvela.member.entitlement.EntitlementGrantStatus;
import solvela.member.entitlement.EntitlementType;
import solvela.member.entitlement.dao.GradeEntitlementDao;
import solvela.member.entitlement.dao.GradeEntitlementGrantDao;
import solvela.member.entitlement.domain.EntitlementCandidate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 生成「待领取」的权益记录。
 *
 * <h3>🔴 幂等靠唯一键，不靠「先查有没有」</h3>
 * {@code uk(member_id, entitlement_id, period_key)} 是这个域的幂等根。
 * 写成「先查有没有再插」的话，两个节点同时扫到同一个人会<b>双双通过</b> ——
 * 而那是多发一份权益，不报错、不留痕，只有月底对预算时才发现多花了钱。
 *
 * <p>所以这里是<b>直接插，撞键就跳过</b>。这也让 job 可以放心地每天扫全量：
 * 昨天已经生成过的今天插不进去，代价只是一次失败的 INSERT。
 *
 * <h3>为什么每天都扫，而不是月度券只在 1 号扫</h3>
 * 只在 1 号扫的话，那天 job 没跑起来（部署、故障、机器睡着了）就是
 * <b>整整一个月的权益没发</b>，而且要等到下个月才有下一次机会。
 * 每天扫 + 唯一键幂等，等于自带补跑。
 *
 * <h3>⚠️ 扫描是分页的，且游标按【扫到的】最大会员号推</h3>
 * 与 {@code MemberGrowthReconcileService} 同一条：按「生成了几条」推游标的话，
 * 一批人全都已经有记录时就拿不到新游标，扫描原地打转。
 *
 * @author alaric
 * @date 2026-09-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GradeEntitlementGrantService {

    private final GradeEntitlementDao gradeEntitlementDao;
    private final GradeEntitlementGrantDao gradeEntitlementGrantDao;

    /** 单批扫多少人。小一点，跑不完下一轮接着来 */
    private static final int BATCH = 500;

    /** 单次执行最多扫几批。活锁保护：游标不推进时不至于无限循环 */
    private static final int MAX_ROUND = 200;

    /**
     * 生成结果。
     *
     * <p>{@code scanned} 也要报：只报「生成了几条」的话，「今天该发的都发过了」
     * 和「压根没扫到人」在输出上<b>长得一模一样</b> ——
     * 而后者才是该报警的那种（游标写错、等级条件写错、库连错）。
     */
    @Getter
    public static class GenerateResult {
        private int scanned;
        private int created;
        private int skipped;

        public String summary() {
            return String.format("扫描 %d 人次，新建待领取 %d 条（已存在跳过 %d）",
                    scanned, created, skipped);
        }
    }

    /**
     * 跑一轮：按今天生成所有启用中的权益，并把到期未领的置为过期。
     *
     * @param today       今天。<b>由调用方从数据库时钟取</b>，不要在这里 now() ——
     *                    多实例下各节点 JVM 时钟未必一致，跨零点那一刻会算成两个周期
     * @param cancelCheck 每批开头调一次，用于响应任务超时/取消
     */
    public GenerateResult generate(LocalDate today, Runnable cancelCheck) {
        GenerateResult result = new GenerateResult();

        List<GradeEntitlement> entitlements = gradeEntitlementDao.selectList(
                new LambdaQueryWrapper<GradeEntitlement>()
                        .eq(GradeEntitlement::getStatus, EnableStatusEnum.ENABLED));
        if (entitlements.isEmpty()) {
            log.debug("【权益】没有启用中的权益配置，本轮什么都不做");
            return result;
        }

        for (GradeEntitlement entitlement : entitlements) {
            if (cancelCheck != null) {
                cancelCheck.run();
            }
            generateOne(entitlement, today, result, cancelCheck);
        }
        return result;
    }

    private void generateOne(GradeEntitlement entitlement, LocalDate today,
                             GenerateResult result, Runnable cancelCheck) {
        String type = entitlement.getEntitlementType();
        if (!EntitlementType.isKnown(type)) {
            /*
             * 不认识的类型只能报警，不能猜。猜成「月度」会让一份本该一年发一次的权益
             * 变成一年发十二次 —— 而这条路上没有任何东西会拦住它。
             */
            log.error("【权益】配置 {}（{}）的类型 [{}] 不认识，本轮跳过。"
                            + "能发的类型只有 BIRTHDAY / MONTHLY，加新类型要写代码",
                    entitlement.getEntitlementCode(), entitlement.getEntitlementName(), type);
            return;
        }

        String periodKey = EntitlementType.periodKeyOf(type, today);
        boolean birthday = EntitlementType.BIRTHDAY.equals(type);

        /*
         * 🔴 闰年生日：平年的 2 月 28 日把 2 月 29 出生的人也算作今天过生日。
         *
         * 不做这件事的话，那批人【四年里有三年收不到生日礼】——
         * 而这件事不报错、没有日志、没有任何人会发现，直到某个 2 月 29 出生的用户来问。
         */
        boolean includeFeb29 = birthday && today.getMonthValue() == 2
                && today.getDayOfMonth() == 28 && !today.isLeapYear();

        int minGrade = entitlement.getMinGrade() == null ? 0 : entitlement.getMinGrade();
        LocalDateTime expireTime = today.atStartOfDay()
                .plusDays(entitlement.getClaimDays() == null ? 30 : entitlement.getClaimDays());

        long cursor = 0L;
        for (int round = 0; round < MAX_ROUND; round++) {
            if (cancelCheck != null) {
                cancelCheck.run();
            }
            List<EntitlementCandidate> batch = gradeEntitlementGrantDao.selectCandidates(
                    minGrade,
                    birthday ? today.getMonthValue() : null,
                    birthday ? today.getDayOfMonth() : null,
                    includeFeb29,
                    cursor,
                    BATCH);
            if (batch.isEmpty()) {
                break;
            }
            for (EntitlementCandidate candidate : batch) {
                result.scanned++;
                // 游标按【扫到的】最大会员号推，不是按生成了几条 —— 一批全都已有记录也要往前走
                cursor = Math.max(cursor, candidate.getMemberId());
                insertPending(entitlement, candidate, periodKey, expireTime, result);
            }
            if (batch.size() < BATCH) {
                break;
            }
        }
    }

    private void insertPending(GradeEntitlement entitlement, EntitlementCandidate candidate,
                               String periodKey, LocalDateTime expireTime, GenerateResult result) {
        GradeEntitlementGrant grant = new GradeEntitlementGrant();
        grant.setEntitlementId(entitlement.getId());
        // 编码快照：配置改名之后，历史记录仍是当时那个
        grant.setEntitlementCode(entitlement.getEntitlementCode());
        grant.setMemberId(candidate.getMemberId());
        grant.setPeriodKey(periodKey);
        grant.setGradeCode(candidate.getGradeCode());
        grant.setStatus(EntitlementGrantStatus.PENDING);
        grant.setExpireTime(expireTime);
        /*
         * 🔴 发放单号在【生成时】就定下来并落库，不是领取时现生成。
         *
         * 现生成的话，一次超时重试会换一个新号 —— 而资产侧的防重唯一键
         * （券 uk_source / 现金 UNIQUE(biz_ref_id, asset_type)）认的正是这个号，
         * 换了号就等于两道防重同时失效，重试一次多发一份。
         */
        grant.setGrantBizId(SolvelaCodeUtil.generateTradeNo("ENT"));

        try {
            gradeEntitlementGrantDao.insert(grant);
            result.created++;
        } catch (DuplicateKeyException e) {
            // 撞唯一键 = 这个周期已经给过他了。这是 job 每天扫全量的正常结果，不是错误
            result.skipped++;
        }
    }

    /**
     * 把到期还没领的置为已过期。
     *
     * <p>⚠️ 分批做，不是一条 UPDATE 扫全表：这张表会随时间一直长，
     * 而一条无界 UPDATE 在表大了之后会长时间持锁，正好压在领取的热路径上。
     */
    public int expire(LocalDateTime now) {
        int total = 0;
        for (int round = 0; round < MAX_ROUND; round++) {
            int rows = gradeEntitlementGrantDao.expirePending(now, BATCH);
            total += rows;
            if (rows < BATCH) {
                break;
            }
        }
        return total;
    }
}
