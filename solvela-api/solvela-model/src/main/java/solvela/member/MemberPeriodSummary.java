package solvela.member;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员周期结算快照。
 *
 * <h3>🔴 它补的不是「历史查不到」，是两个更具体的洞</h3>
 * <ol>
 *   <li><b>等级没变的周期，全库一行记录都没有</b> ——
 *       {@code MemberGradeChangeService.change()} 在 old == new 且非 KEEP 时直接返回，
 *       于是「平级过完一个周期」的人在 {@link MemberGradeLog} 里是空的。
 *       而「我上周期到底攒了多少、差多少」正是客诉最常问的那句；</li>
 *   <li><b>对账任务没有锚点</b> —— 方案 §5.2 要求比对
 *       {@code current_period_value} 与流水求和，但前者<b>一直在动</b>。
 *       有了快照，对账变成「比对一个封存的数」。</li>
 * </ol>
 *
 * <h3>⚠️ {@code periodNo} 必须与 {@code MemberGrowthLog.periodTag} 字节一致</h3>
 * 都是 {@code periodStart} 的 {@code yyyyMMdd}。对账任务靠它 JOIN 两张表；
 * 一边写 yyyyMM、一边写 yyyyMMdd 的话，对账会<b>安静地什么都对不上</b>。
 *
 * @author alaric
 * @date 2026-09-20
 */
@Data
@TableName("t_member_period_summary")
public class MemberPeriodSummary {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long memberId;

    /** 周期标识 = periodStart 的 yyyyMMdd。与成长值流水的 periodTag 同口径 */
    private String periodNo;

    private LocalDateTime periodStart;

    private LocalDateTime periodEnd;

    /** 期末最终成长值。清零前的那个数，事后唯一凭证 */
    private Long finalGrowthValue;

    private Integer gradeBefore;

    private Integer settledGrade;

    /** 见 {@code GradeSettleResult} */
    private String settleResult;

    /** 结算时的下一档。已是最高档为 null */
    private Integer nextGrade;

    /**
     * 下一档门槛的<b>当时快照</b>。
     *
     * <p>⚠️ 必须存：运营改过门槛之后，拿今天的配置回算会算出另一个答案 ——
     * 而客诉问的是「我<b>当时</b>差多少」。
     */
    private Long nextThreshold;

    /** 该周期若处于保级缓冲，保的是哪一档 */
    private Integer protectGrade;

    /** 结算发生的时刻。不是周期结束时刻 —— job 可能晚跑，两者要能分开看 */
    private LocalDateTime settledAt;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
