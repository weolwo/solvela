package solvela.prize.prizeconfig.domain.command;


import solvela.enums.ApproveModeEnum;
import java.math.BigDecimal;

import lombok.Data;
import solvela.base.util.SolvelaCodeUtil;

/**
 * 新增奖品的<b>领域命令</b>。与管理端的 {@code PrizeConfigAddCommand} 形状一致，但职责不同：
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
public class PrizeConfigAddCommand {

    /** 归属活动编码 */
    private String activityCode;

    /** 优惠配置ID，关联 t_promotion_config，承载预算与风控；标记(MARKER)类奖品可空 */
    private Long promotionConfigId;

    /** 资产类型：SCORE, BALANCE, COUPON, PHYSICAL, MARKER, LOTTERY, CUSTOM */
    private String prizeType;

    /** 奖品名称 */
    private String prizeName;

    /** 奖品编码：10 位大写字母+数字，全局唯一 */
    private String prizeCode;

    /** 奖品级别 */
    private Integer prizeLevel;

    /** 奖励价值 */
    private BigDecimal prizeValue;

    /** 审批模式：0-自动免审, 1-人工审批 */
    private ApproveModeEnum approveMode;

    /** 排序权重 */
    private Integer sortWeight;

    /** 扩展信息：如奖品图片URL、跳转链接等 */
    private String ext;

}