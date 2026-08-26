package solvela.activity.domain.command;


import java.time.LocalDateTime;

import lombok.Data;
import solvela.base.util.SolvelaCodeUtil;
import solvela.base.validation.enumeration.CheckEnum;
import solvela.enums.ActivityTypeEnum;

/**
 * 新增活动的<b>领域命令</b>。与管理端的 {@code ActivityConfigAddCommand} 形状一致，但职责不同：
 *
 * <ul>
 *   <li>Form 是 HTTP 请求体：{@code @Schema} 描述接口文档、{@code @NotNull} 等校验
 *       前端传没传、传得对不对 —— 这些都跟着某个端的页面走；</li>
 *   <li>Command 是领域入参：service 对它做的是<b>业务不变量</b>校验
 *       （编码是否重复、状态能否流转、关联配置是否匹配），与谁调用无关。</li>
 * </ul>
 *
 * <p>合成一个的代价：C 端将来若要写入，得构造一个带管理端校验规则的表单；
 * 而共享层也会一直依赖 springdoc 与 jakarta.validation 这些 HTTP 层的概念。
 *
 * <p>分层说明见 {@code MemberWalletQuery}。
 */

@Data
public class ActivityConfigAddCommand {

    /** 活动编码：10 位大写字母+数字，全局唯一 */
    private String activityCode;

    /** 活动名称 */
    private String activityName;

    /** 活动类型：BASIC-基础活动 / DRAW-奖池抽奖 / TASK-任务驱动 / LOTTERY-FPE彩票 */
    @CheckEnum(value = ActivityTypeEnum.class, required = true, message = "活动类型非法")
    private String activityType;

    /** 状态：0-未开始, 1-上线, 2-下线 */
    private Integer status;

    /** 活动开始时间 */
    private LocalDateTime startTime;

    /** 活动结束时间 */
    private LocalDateTime endTime;

}