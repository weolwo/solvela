package solvela.member.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * C 端等级页要的全部东西，一次拿完。
 *
 * <h3>🔴 为什么是一个大 view 而不是让前端拼三个接口</h3>
 * 等级页上「我在哪一档 / 还差多少 / 本期什么时候截止 / 我在保什么」是<b>一句话</b>，
 * 拆成三个接口意味着三次往返、三个 loading，而且中间任何一个失败页面就是半截的。
 *
 * @param gradeCode       当前等级
 * @param gradeName       当前等级名
 * @param gradeSince      这一档是什么时候到的
 * @param currentValue    本周期累计成长值
 * @param totalValue      终身累计，只展示
 * @param periodEnd       本周期截止
 * @param nextGradeCode   下一档；已是最高档为 null
 * @param nextGradeName   下一档名
 * @param nextThreshold   下一档门槛
 * @param gapToNext       还差多少；已是最高档为 null
 * @param inProtect       是不是正在保级缓冲期
 * @param protectUntil    缓冲期截止；不在缓冲期为 null
 * @param protectGrade    在保哪一档
 * @param protectGap      保住还差多少；已达标为 0
 * @param boostMultiplier 当前成长值倍率（缓冲期是 2，平时是 1）
 * @param ladder          完整阶梯，含每一档的权益 —— 用户要看得见「再上一档能拿什么」
 * @author alaric
 * @date 2026-09-20
 */
public record MemberGradeView(
        Integer gradeCode,
        String gradeName,
        LocalDateTime gradeSince,
        Long currentValue,
        Long totalValue,
        LocalDateTime periodEnd,
        Integer nextGradeCode,
        String nextGradeName,
        Long nextThreshold,
        Long gapToNext,
        Boolean inProtect,
        LocalDateTime protectUntil,
        Integer protectGrade,
        Long protectGap,
        Integer boostMultiplier,
        List<GradeLadderView> ladder) {
}
