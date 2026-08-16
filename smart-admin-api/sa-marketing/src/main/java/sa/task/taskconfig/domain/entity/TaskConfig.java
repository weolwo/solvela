package sa.task.taskconfig.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 任务配置表 实体类
 *
 * @Author weolwo
 * @Date 2026-04-18 20:55:10
 * @Copyright weolwo
 */

@Data
@TableName("t_task_config")
public class TaskConfig {

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
     * 租户ID
     */
    private String tenantId;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 模板Code
     */
    private String templateCode;

    /**
     * 触发事件：ORDER_PAID(支付), MEMBER_REGISTER(注册), DAILY_SIGN(签到), PAGE_VIEW(浏览), CUSTOM(自定义)
     */
    private String triggerEvent;

    /**
     * 任务分组：NEWBIE(新手), DAILY(日常), PROMO(大促), VIP(会员专属)
     */
    private String taskGroup;

    /**
     * 目标人群：ALL(全部), NEW_MEMBER(新会员), OLD_MEMBER(老会员)
     */
    private String targetAudience;

    /**
     * 参与频次：ONCE(终身一次), DAILY(每日重复), WEEKLY(每周重复), UNLIMITED(无限制)
     */
    private String limitType;

    /**
     * 配合频次类型，限制次数
     */
    private Integer limitCount;

    /**
     * 规则配置
     */
    private String ruleConfig;

    /**
     * 排序权重，越大越靠前
     */
    private Integer sortWeight;

    /**
     * 跳转地址
     */
    private String actionUrl;

    /**
     * 展示UI(图标/角标等)
     */
    private String uiConfig;

    /**
     * 任务状态 1-待生效, 2-生效中, 3-已下线
     */
    private Integer status;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

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
