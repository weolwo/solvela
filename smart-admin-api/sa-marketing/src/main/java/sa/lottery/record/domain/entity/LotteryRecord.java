package sa.lottery.record.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户号码记录 实体类
 *
 * @Author weolwo
 * @Date 2026-04-19 11:57:08
 * @Copyright weolwo
 */

@Data
@TableName("t_lottery_record")
public class LotteryRecord {

    /**
     * 主键id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户id
     */
    private String tenantId;

    /**
     * 彩票编码
     */
    private String lotteryCode;

    /**
     * 期号
     */
    private String issueNo;

    /**
     * FPE算号基数
     */
    private Integer sequenceNo;

    /**
     * 彩票号码
     */
    private String ticketNumber;

    /**
     * 会员号：关联键（v3.71.0 换键）。查询、join、对账一律用它。
     */
    private Long memberId;

    /**
     * 会员账号 —— <b>展示快照，不是关联键</b>（会员改名后不跟着变）。
     *
     * <p>🔴 这一列还有一个别处没有的用途：它是 {@code security_sign} 的<b>签名要素之一</b>
     * （见 {@code TicketSignService}）。签名口径一旦改动，<b>存量号码全部验真失败</b>，
     * 所以领号时签的是账号快照、验真时也读这一列，两边必须始终一致。
     * 正因为快照永不变，它反而是比 member_id 更稳妥的签名要素 —— 换成会员号并不会更安全，
     * 只会让已经发出去的票据在下一次验真时集体报「疑似篡改」。
     *
     * <p>除签名之外<b>不要拿它做查询条件</b>：这一列身上已经没有索引了。
     */
    private String memberName;

    /**
     * 领号时间
     */
    private LocalDateTime obtainTime;

    /**
     * 中奖状态: 0-未开奖, 1-未中奖, 2-已中奖
     */
    private Integer winStatus;

    /**
     * 奖励等级
     */
    private Integer prizeLevel;

    /**
     * 中奖奖品编码：核销时从规则表快照，防规则被改后历史中奖结果漂移（v3.40 新增）
     */
    private String prizeCode;

    /**
     * 派发状态：0-待派发/无需派发, 1-已投递, 2-投递失败（v3.40 新增）
     */
    private Integer dispatchStatus;

    /**
     * 防篡改签名
     */
    private String securitySign;

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
