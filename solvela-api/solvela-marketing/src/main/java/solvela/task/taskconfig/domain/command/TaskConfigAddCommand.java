package solvela.task.taskconfig.domain.command;

import solvela.enums.TaskConfigStatusEnum;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 任务配置表 新建表单
 *
 * @Author weolwo
 * @Date 2026-04-18 20:55:10
 * @Copyright weolwo
 */

@Data
public class TaskConfigAddCommand {

    /** 活动编码 */
    private String activityCode;

    /** 任务名称 */
    private String taskName;

    /** 模板Code */
    private String templateCode;

    /** 触发事件：ORDER_PAID(支付), MEMBER_REGISTER(注册), DAILY_SIGN(签到), PAGE_VIEW(浏览), CUSTOM(自定义) */
    private String triggerEvent;

    /** 任务分组：NEWBIE(新手), DAILY(日常), PROMO(大促), VIP(会员专属) */
    private String taskGroup;

    /** 目标人群：ALL(全部), NEW_MEMBER(新会员), OLD_MEMBER(老会员) */
    private String targetAudience;

    /** 参与频次：ONCE(终身一次), DAILY(每日重复), WEEKLY(每周重复), UNLIMITED(无限制) */
    private String limitType;

    /** 配合频次类型，限制次数 */
    private Integer limitCount;

    /** 规则配置 */
    private String ruleConfig;

    /** 排序权重，越大越靠前 */
    private Integer sortWeight;

    /** 跳转地址 */
    private String actionUrl;

    /** 展示UI(图标/角标等) */
    private String uiConfig;

    /** 任务状态 1-待生效, 2-生效中, 3-已下线 */
    private TaskConfigStatusEnum status;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

}