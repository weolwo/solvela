package solvela.member;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 成长值流水。
 *
 * <h3>🔴 {@code UNIQUE(source, biz_id)} 是这条链路防重的全部依靠</h3>
 * 上游打点是<b>至少一次</b>的（对账 job 会重推漏投的）。没有这个唯一键，
 * 同一笔积分入账会被加两次成长值 —— 而成长值换的是长期权益。
 *
 * <p>形状照抄 {@code t_task_record_flow.uk_t_tsk_flw_evt}，那条已经在生产里验证过。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Data
@TableName("t_member_growth_log")
public class MemberGrowthLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long memberId;

    /** 本次增减的成长值，<b>已经乘过倍率</b> */
    private Long delta;

    /**
     * 倍率之前的基数。
     *
     * <p>🔴 和 {@link #multiplier} 一起存，是为了客诉时能说清
     * 「为什么是 200 不是 100」。只存 delta 的话，那个问题答不了 ——
     * 而保级期加倍恰恰是用户最会来问的那一次。
     */
    private Long baseValue;

    /** 本次倍率：1-常态, 2-保级缓冲期 */
    private Integer multiplier;

    /** 变动后的周期累计值 —— 对账锚点 */
    private Long afterPeriodValue;

    /** 来源：{@code SCORE_EARNED}，将来的 {@code BIND_PHONE} 等 */
    private String source;

    /**
     * 上游业务类型，如 {@code PROPOSAL_REWARD}。
     *
     * <p>🔴 <b>「算不算成长值」的判据就是它</b>，不是方法名 ——
     * 资产域的 {@code executeWalletRefund} 同时承担「商城发积分」和「真正的退回」，
     * 按方法判会把商城发的积分一起漏掉。详见方案 §4.2。
     */
    private String bizType;

    /** 上游业务单号：<b>幂等键</b> */
    private String bizId;

    /**
     * 计入哪个周期（{@code period_start} 的 yyyyMMdd）。
     *
     * <p>🔴 <b>保级缓冲期攒的成长值计入「上一周期」</b>，只用于保级判定。
     * 保住了，新周期仍从 0 开始。
     *
     * <p>这一条和「倍率不设上限」是一对：不设上限是给用户的慷慨
     * （一笔大额消费就能冲回钻石），只归上一周期是堵套利的 ——
     * 否则用户会故意让自己掉到边缘吃双倍，再带着翻倍的成长值进新周期。
     * 拆开任何一半，另一半都会变成漏洞。
     */
    private String periodTag;

    /** C 端展示摘要 */
    private String remark;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
