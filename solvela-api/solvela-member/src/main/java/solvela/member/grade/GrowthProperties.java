package solvela.member.grade;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Period;
import java.util.List;

/**
 * 成长值口径。
 *
 * <h3>🔴 为什么是配置而不是一张规则表</h3>
 * 今天成长值<b>只有一个来源</b>（积分入账），一个来源不需要规则表 ——
 * 建了就是两套配置要对齐，而那正是「成长值让业务复杂很多」的主要成本。
 *
 * <p>真加到第三、第四个来源时再建表。到那时它是一次有明确收益的重构，
 * 而不是现在这样为还没有的问题付钱。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Data
@Component
@ConfigurationProperties(prefix = "solvela.member.growth")
public class GrowthProperties {

    /**
     * 考核周期长度。默认 12 个月，<b>从入会日起算</b>。
     *
     * <p>🔴 不用自然年：那样全平台会在同一天结算，一次跑 9000+ 会员的降级判定，
     * 而且所有人的「保级冲刺」挤在年底同一周 —— 运营侧和系统侧同时到峰值。
     * 按各人的会员年度日分散开，两边都平滑。
     */
    private Period period = Period.ofMonths(12);

    /**
     * 积分入账里，<b>哪些 bizType 算成长值</b>。
     *
     * <h3>🔴 判据只能是 bizType，不能是「哪个方法写的流水」</h3>
     * 资产域的 {@code executeWalletRefund} 同时承担两件事：
     * <ul>
     *   <li>{@code AssetGrantApiService.grantWallet} —— <b>商城/发放入账</b>，该算；</li>
     *   <li>{@code AssetDebitApiService.refund} —— <b>真正的退回</b>，不该算。</li>
     * </ul>
     * 按方法跳过它，会把商城发的积分一起漏掉。
     *
     * <h3>为什么是白名单不是黑名单</h3>
     * 将来新增一种入账来源时，白名单的默认行为是<b>不算</b> ——
     * 那是安全的一边。黑名单的默认行为是「算」，于是任何一个没人想到要排除的
     * 新来源都会悄悄抬高所有人的等级，而且没有任何报错。
     *
     * <p>默认只有 {@code PROPOSAL_REWARD}（发奖入账）—— 库里 4413 条 SCORE 收入
     * 全是它（2026-09-18 实测）。
     */
    private List<String> countableBizTypes = List.of("PROPOSAL_REWARD");

    /**
     * 积分 → 成长值的系数。默认 1:1。
     *
     * <p>⚠️ 它<b>不是</b>倍率。倍率（保级期 2 倍）是按人、按时间窗算的，
     * 这个是全局口径。两者会相乘。
     */
    private int scoreRatio = 1;

    /**
     * 保级缓冲期的成长值倍率。
     *
     * <p>🔴 <b>只作用于成长值，绝不影响积分</b> —— 积分是负债，
     * 翻倍等于凭空多发钱。而这里要给的只是一个够得着的目标。
     *
     * <p>不设上限是刻意的：一笔大额消费就能从 V1 冲回 V4，够得着才有人去够。
     * 套利不是靠封顶堵的，是靠「加速所得只计入上一周期」堵的 —— 两者是一对。
     */
    private int protectMultiplier = 2;

    /**
     * 保级缓冲期有多长。默认 3 个月。
     *
     * <h3>🔴 缓冲期是【当前周期的延长】，不是一个新周期</h3>
     * 进入缓冲时 {@code period_start / period_end / current_period_value} 全都<b>不动</b>，
     * 用户继续往同一个计数器里加（×{@link #protectMultiplier}），缓冲期满才一并结算、清零、推进。
     *
     * <p>这样「加速所得只归上一周期」是<b>自动成立</b>的 —— 缓冲期攒的全在旧计数器里，
     * 期满一起清零，一点都带不进新周期。不需要跨周期求和，也不会出现
     * 「这笔到底算哪个周期」这种只能靠约定维系的东西。
     *
     * <p>⚠️ 副作用是<b>会员年度会被拉长</b>：12 个月 + 缓冲期。这是有意的 ——
     * 缓冲期本来就是「宽限」，它属于没保住的那一年，不该占用下一年的时间。
     */
    private Period protectPeriod = Period.ofMonths(3);
}
