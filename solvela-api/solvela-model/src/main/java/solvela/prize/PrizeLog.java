package solvela.prize;

import solvela.enums.PrizeDispatchStatusEnum;
import solvela.enums.PrizeApproveStatusEnum;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 奖励记录表 实体类
 *
 * @Author weolwo
 * @Date 2026-04-18 20:27:03
 * @Copyright weolwo
 */

@Data
@TableName("t_prize_log")
public class PrizeLog {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会员号：关联键（v3.71.0 换键）。查询、join、对账一律用它。
     */
    private Long memberId;

    /**
     * 会员账号 —— <b>展示快照，不是关联键</b>。
     *
     * <p>记的是「写这条记录当时那个账号」，会员改名之后<b>刻意不跟着变</b>：
     * 单据要回答的是「当时是谁」，这和 {@code t_mall_order} 里存商品名快照是同一个模式。
     *
     * <p>🔴 <b>不要拿它做查询条件</b>：这一列身上已经没有任何索引（v3.71.0 换到 member_id 了），
     * 写 {@code WHERE member_name = ?} 就是全表扫；建索引更不行 —— 关联键会就此悄悄退回
     * member_name，改名断链的问题原样复活。按账号找人先经 {@code MemberService} 换成会员号。
     */
    private String memberName;

    /**
     * 奖品编码
     */
    private String prizeCode;

    /**
     * 活动编码
     */
    private String activityCode;

    /**
     * 奖品级别
     */
    private Integer prizeLevel;

    /**
     * 奖品名称
     */
    private String prizeName;

    /**
     * 奖励类型：SCORE, BALANCE, COUPON, PHYSICAL, MARKER
     */
    private String prizeType;

    /**
     * 奖励体值(积分数/券ID)
     */
    private String prizeValue;

    /**
     * 异常原因
     */
    private String failReason;

    /**
     * 审批状态：0-无需审批, 1-待审批, 2-已批准, 3-已驳回
     */
    private PrizeApproveStatusEnum approveStatus;

    /**
     * 审批人
     */
    private String approveBy;

    /**
     * 审批时间
     */
    private LocalDateTime approveTime;

    /**
     * 过期时间
     */
    private LocalDateTime validUntil;

    /**
     * 执行状态：0-等待, 1-成功, 2-失败
     */
    private PrizeDispatchStatusEnum status;

    /**
     * 外部单号
     */
    private String externalBizNo;

    /**
     * 异常原因
     */
    private String remark;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
