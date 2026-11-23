package solvela.lottery;

import solvela.enums.IssueStatusEnum;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

/**
 * 期号配置 实体类
 *
 * @Author weolwo
 * @Date 2026-05-09 16:54:51
 * @Copyright weolwo
 */

@Data
@TableName("t_lottery_issue")
public class LotteryIssue {

    /**
     * 主键id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 彩票编码
     */
    private String lotteryCode;

    /**
     * 期号
     */
    private String issueNo;

    /**
     * 已售/已派发数量
     */
    private Integer soldCount;

    /**
     * 售卖开始时间
     */
    private LocalDateTime saleStartTime;

    /**
     * 售卖结束时间
     */
    private LocalDateTime saleEndTime;

    /**
     * 计划开奖时间：对外承诺的开奖时刻，可编辑（v3.40 新增）
     */
    private LocalDateTime planDrawTime;

    /**
     * 实际开奖时间：由开奖流程写入，只读。
     * 该列没有 ON UPDATE CURRENT_TIMESTAMP 兜底，必须在开奖 SQL 里显式赋值（铁律 9 的例外分支）
     */
    @Setter(AccessLevel.NONE)
    private LocalDateTime settleTime;

    /**
     * 开奖号码
     */
    @Setter(AccessLevel.NONE)
    private String winningNumber;

    /**
     * 状态: 0-待开奖, 1-核销中, 2-已开奖
     *
     * <p>🔴 没有公开 setter，开奖号码、结算时间也没有：初始状态只能经 {@link #awaitDraw} 定，
     * 之后只走 {@code LotteryIssueDao} 的 startSettle / finishSettle（状态机见 {@link IssueStatusEnum#canTransitTo}）。
     * 开奖号码是在抢到开奖闸门那一刻随状态一起定案的，不能单独改。MyBatis 读库时直接写字段，不需要 setter。
     */
    @Setter(AccessLevel.NONE)
    private IssueStatusEnum status;

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

    /**
     * 落为<b>待开奖</b>：期号唯一的初始状态，可售卖、可改时间、零发号时可删除。
     */
    public void awaitDraw() {
        if (status != null) {
            throw new IllegalStateException("期号 " + id + " 已是 " + status + "，不能再定初始状态");
        }
        this.status = IssueStatusEnum.WAIT;
    }
}
