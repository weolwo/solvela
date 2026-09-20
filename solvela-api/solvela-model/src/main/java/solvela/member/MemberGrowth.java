package solvela.member;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员的成长值与等级，<b>一人一行</b>。
 *
 * <h3>🔴 等级为什么不加到 {@code t_member} 上</h3>
 * 它是<b>派生状态</b>，跟着成长值走。而 {@code t_member} 是身份表 ——
 * 混入会随业务频繁变动的列，等于让身份表跟着营销规则一起抖。
 * 放这里是一次主键点查，代价可以忽略。
 *
 * <h3>🔴 成长值不是积分</h3>
 * 积分是<b>货币</b>（可花、是负债），成长值是<b>经验</b>（只记录、不可花）。
 * 用户在商城花掉积分时，成长值<b>不减少</b> —— 定级看的是「这一周期赚了多少」，
 * 不是「现在剩多少」。
 *
 * <p>今天成长值 1:1 来自积分入账，看起来像多此一举。但<b>保级缓冲期 2 倍</b>
 * 那一刻它们就分家了：用户赚 100 积分、成长值涨 200。
 * 倍率只能作用在成长值上 —— 作用在积分上就是凭空多发负债。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Data
@TableName("t_member_growth")
public class MemberGrowth {

    /** 会员号：关联键，同时是主键（一人一行） */
    @TableId(type = IdType.INPUT)
    private Long memberId;

    /**
     * 当前等级。
     *
     * <p>⚠️ <b>缓冲期内它等于 {@link #protectGrade}，不等于 f(periodValue)</b> ——
     * 那是纯映射唯一的例外，代价是 UI 必须显式标「保级中」。
     */
    private Integer currentGrade;

    /** 当前等级是什么时候到的 */
    private LocalDateTime gradeSince;

    /** 本考核周期开始 */
    private LocalDateTime periodStart;

    /** 本考核周期结束 = periodStart + 12 个月 */
    private LocalDateTime periodEnd;

    /**
     * 本周期累计成长值 —— <b>定级依据</b>。
     *
     * <p>🔴 这是<b>冗余</b>：它能由 {@code t_member_growth_log} 求和得出。
     * 冗余是因为判级在热路径上，每次扫流水不现实。
     *
     * <p><b>冗余就必须有对账</b>：定期比对本值与流水求和，对不上要告警。
     * 冗余而不对账，迟早出现「明细和总数对不上」，而那时没人知道哪个是对的。
     */
    private Long currentPeriodValue;

    /** 终身累计成长值。<b>只用于展示，不参与定级</b> */
    private Long totalValue;

    /** 保级缓冲到期时刻；为空表示不在缓冲期 */
    private LocalDateTime protectUntil;

    /** 缓冲期要保住的等级 */
    private Integer protectGrade;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
