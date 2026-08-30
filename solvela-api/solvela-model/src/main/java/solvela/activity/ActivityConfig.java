package solvela.activity;

import solvela.enums.ActivityStatusEnum;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 活动配置 实体类
 *
 * @Author weolwo
 * @Date 2026-04-18 19:31:49
 * @Copyright weolwo
 */

@Data
@TableName("t_activity_config")
public class ActivityConfig {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 活动编码
     */
    private String activityCode;

    /**
     * 活动名称
     */
    private String activityName;

    /**
     * 活动类型
     */
    private String activityType;

    /**
     * 状态：0-未开始, 1-上线, 2-下线
     */
    private ActivityStatusEnum status;

    /**
     * 活动开始时间
     */
    private LocalDateTime startTime;

    /**
     * 活动结束时间
     */
    private LocalDateTime endTime;

    /**
     * 数据截止时间：此刻起<b>不再受理参与</b>（抽奖、任务累计），但活动仍可见、已中的奖仍可领到 {@link #endTime}。
     *
     * <p>三个时间窗：
     * <pre>
     *   startTime ──── dataEndTime ──── endTime
     *   [ 可参与、可领奖 ][ 只可领奖、可查看 ][ 已结束 ]
     * </pre>
     *
     * <p><b>允许为空，为空即等同于 {@link #endTime}</b>。绝大多数活动不区分这两件事。
     * 刻意不给具体默认值 —— 有默认值的话，「没配」和「配成与结束时间相同」看起来一模一样，
     * 将来想区分就区分不了了。
     */
    private LocalDateTime dataEndTime;

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
