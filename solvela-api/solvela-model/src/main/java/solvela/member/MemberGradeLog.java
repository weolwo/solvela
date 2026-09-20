package solvela.member;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 等级变更留痕。
 *
 * <h3>🔴 它和那三张表一样是第一版的一部分，不是「以后再补」</h3>
 * 用户会<b>真的来问</b>「我为什么掉级了」—— 这件事和「积分对不上」并列，
 * 是客服最常被问到的两件事。答不上来的会员体系，用几次就没人信了。
 *
 * <p>而这个问题的答案只能在变更发生的<b>那一刻</b>记下来：
 * 事后拿当前成长值去反推，会因为周期已经翻篇而永远算不回去。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Data
@TableName("t_member_grade_log")
public class MemberGradeLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long memberId;

    private Integer oldGrade;

    private Integer newGrade;

    /** UPGRADE-升级 / DOWNGRADE-降级 / KEEP-保级 / MANUAL-人工调整 / RISK_REVOKE-风控扣回 */
    private String changeType;

    /**
     * 变更时的周期成长值快照。
     *
     * <p>🔴 <b>事后复盘唯一的依据</b>。不存的话，周期一翻篇就再也算不回
     * 「他当时到底有多少」—— 而那正是客诉要问的那个数。
     */
    private Long periodValue;

    /** 原因。人工调整时必填 */
    private String reason;

    /** 操作人：系统变更为空，人工调整记员工 */
    private String operator;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
