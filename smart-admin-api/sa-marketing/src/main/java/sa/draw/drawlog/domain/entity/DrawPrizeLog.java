package sa.draw.drawlog.domain.entity;

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
     * 租户id
     */
    private String tenantId;

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
     * 会员名
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
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
