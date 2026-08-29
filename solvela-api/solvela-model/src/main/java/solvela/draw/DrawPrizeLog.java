package solvela.draw;

import solvela.enums.DrawResultEnum;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 抽奖记录 实体类
 *
 * @Author weolwo
 * @Date 2026-04-19 09:21:26
 * @Copyright weolwo
 */

@Data
@TableName("t_draw_prize_log")
public class DrawPrizeLog {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 请求ID
     */
    private String traceId;

    /**
     * 活动编码
     */
    private String activityCode;

    /**
     * 奖池编码
     */
    private String poolCode;

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
     * 奖项ID
     */
    private Long prizeItemId;

    /**
     * 奖品code
     */
    private String prizeCode;

    /**
     * 状态: 0-未中奖, 1-已中奖, 2-库存不足, 3-异常
     */
    private DrawResultEnum status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
