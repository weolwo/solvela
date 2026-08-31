package solvela.activity.domain.command;


import solvela.enums.ActivityStatusEnum;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 更新活动的<b>领域命令</b>。与管理端的 {@code ActivityConfigUpdateCommand} 形状一致，但职责不同：
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
public class ActivityConfigUpdateCommand {

    private Long id;

    /** 活动名称 */
    private String activityName;

    /*
     * ⚠️ 刻意不定义 activityType —— 活动类型创建后不可通过普通编辑修改。
     *
     * 类型决定了下游整块配置挂在哪套表上：一个 DRAW 活动下已配的奖池、物资、坑位映射
     * 全部以 activity_code 关联，改成 LOTTERY 后这些行仍在库里，
     * 但 LotteryWorkbench 查不到、DrawWorkbench 也不再列出该活动（它按 activityType 过滤下拉）
     * —— 数据既删不掉也看不见。
     *
     * 不定义字段，让越权意图在编译期就无处安放（同 LotteryIssueUpdateForm 对 issueNo 的处理）。
     * 唯一合法的类型变更是 BASIC → 玩法类的升级，走独立窄接口 /activityConfig/upgradeType，
     * 服务端会校验「当前必须是 BASIC」+「下游玩法表为空」两条。
     */

    private ActivityStatusEnum status;

    /** 活动开始时间 */
    private LocalDateTime startTime;

    /** 活动结束时间 */
    private LocalDateTime endTime;

    /** 数据截止时间：此刻起不再受理参与，但奖品仍可领到 endTime。为空表示与 endTime 相同 */
    private LocalDateTime dataEndTime;

}