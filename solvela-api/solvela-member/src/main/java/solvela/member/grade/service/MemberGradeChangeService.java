package solvela.member.grade.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import solvela.member.MemberGradeLog;
import solvela.member.grade.GradeChangeType;
import solvela.member.grade.dao.MemberGrowthDao;
import solvela.member.grade.dao.MemberGradeLogDao;

/**
 * 等级变更的<b>唯一写入口</b>。
 *
 * <h3>🔴 为什么要独占这一件事</h3>
 * 等级会从四个地方变：入账升级（阶段 2）、期末降级、缓冲期保级（阶段 4）、
 * 人工调整（阶段 2）。每一处都得做同样的三件事 ——
 * 条件更新、写留痕、带上成长值快照。
 *
 * <p>散在四处的结果是可以预见的：<b>总有一处会忘记留痕</b>。
 * 而忘记留痕这件事不会报错、不影响任何功能，只会在半年后某个用户
 * 问「我为什么掉级了」的时候，变成一句「查不到」。
 *
 * <h3>🔴 留痕里的 periodValue 是调用方传进来的快照，不是这里查的</h3>
 * 这里再查一次的话，查到的是<b>变更之后</b>的值；而客诉要问的恰恰是
 * 「变更那一刻他有多少」。两者在并发入账时会差一笔，
 * 那一笔正好就是导致升级的那一笔。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberGradeChangeService {

    private final MemberGrowthDao memberGrowthDao;
    private final MemberGradeLogDao memberGradeLogDao;

    /**
     * 把等级从 {@code oldGrade} 改成 {@code newGrade}，并留一条痕。
     *
     * <p>🔴 {@code REQUIRED}（默认传播）：调用方已经在事务里（入账、结算、
     * 管理端操作都是），等级和留痕必须跟着调用方一起成败。
     * 用 {@code REQUIRES_NEW} 的话会出现「成长值回滚了但等级留下来」，
     * 那是最难查的一种脏数据 —— 它看起来完全正常。
     *
     * @param memberId    会员号
     * @param oldGrade   调用方看到的当前等级，会进 WHERE 做并发判据
     * @param newGrade     目标等级
     * @param changeType  见 {@link GradeChangeType}
     * @param periodValue 变更那一刻的周期成长值<b>快照</b>
     * @param reason      原因。{@code MANUAL} 时必填，系统变更可空
     * @param operator    操作人。系统变更传 {@code null}
     * @return 真的改了返回 true；等级已被别人改过（影响 0 行）返回 false
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public boolean change(Long memberId, int oldGrade, int newGrade, String changeType,
                          long periodValue, String reason, String operator) {
        if (oldGrade == newGrade && !GradeChangeType.KEEP.equals(changeType)) {
            /*
             * 等级没变还要写一条留痕，只有「保级」一种情况 —— 那是用户要看见的好消息。
             * 其余情况（比如重复点了人工调级）写进去就是一条噪音，
             * 而留痕表的价值全在于「每一条都意味着发生过什么」。
             */
            log.debug("【会员等级】等级未变且非保级，不留痕。memberId={}, level={}", memberId, oldGrade);
            return false;
        }

        int changed = memberGrowthDao.updateGrade(memberId, oldGrade, newGrade);
        if (changed == 0) {
            /*
             * 不是错误：并发入账时两条线程都算出「该升到 V2」，先到的那条改成了 V2，
             * 后到的 WHERE level = V1 就落空了。此时等级已经是对的，本次什么都不用做。
             */
            log.debug("【会员等级】等级已被并发改动，本次跳过。memberId={}, from={}, to={}",
                    memberId, oldGrade, newGrade);
            return false;
        }

        MemberGradeLog logRow = new MemberGradeLog();
        logRow.setMemberId(memberId);
        logRow.setOldGrade(oldGrade);
        logRow.setNewGrade(newGrade);
        logRow.setChangeType(changeType);
        logRow.setPeriodValue(periodValue);
        logRow.setReason(reason);
        logRow.setOperator(operator);
        memberGradeLogDao.insert(logRow);

        log.info("【会员等级】{} {} → {}，memberId={}, 周期成长值={}, 操作人={}",
                changeType, oldGrade, newGrade, memberId, periodValue,
                operator == null ? "系统" : operator);
        return true;
    }
}
