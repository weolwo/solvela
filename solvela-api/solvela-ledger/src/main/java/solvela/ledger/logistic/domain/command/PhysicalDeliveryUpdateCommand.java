package solvela.ledger.logistic.domain.command;

import lombok.Data;

/**
 * 更新实物履约单的<b>领域命令</b>。与管理端的 {@code PhysicalDeliveryUpdateCommand} 形状一致，但职责不同：
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
public class PhysicalDeliveryUpdateCommand {

    private Long id;

    /*
     * 🔴 下面三个字段有<b>密文列宽的硬约束</b>（姓名 40 / 电话 30 / 地址 100 字符）。
     * 这里刻意没有 {@code @Size} —— 校验注解是端的东西，共享层不挂。
     * 真正的守卫在 {@code PhysicalDeliveryService.checkPii}，写入前必过，
     * 算式与后果都写在那个方法的注释里。管理端 Form 上的同名 {@code @Size} 只是提前红框。
     */
    private String receiverName;

    /** 收件人电话（密文落库） */
    private String receiverPhone;

    /** 收件详细地址（密文落库） */
    private String receiverAddress;

    /** 物流公司 */
    private String logisticsCompany;

    /** 物流单号 */
    private String logisticsNo;

    /** 状态：0-待发货, 1-已发货, 2-已签收, 3-异常退回 */
    private Integer status;

}