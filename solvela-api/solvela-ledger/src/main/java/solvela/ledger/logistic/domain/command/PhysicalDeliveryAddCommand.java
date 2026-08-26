package solvela.ledger.logistic.domain.command;

import lombok.Data;

/**
 * 新增实物履约单的<b>领域命令</b>。与管理端的 {@code PhysicalDeliveryAddCommand} 形状一致，但职责不同：
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
public class PhysicalDeliveryAddCommand {

    /**
     * 会员号 —— 关联键。调用方只需给它，账号快照由服务端查会员表补
     * （见 {@code MemberService.requireMemberName}），这样快照与会员号<b>不可能对不上</b>。
     */
    private Long memberId;

    /**
     * 来源单号。原先这里叫 proposalId(Long)，写死了「履约单必来自发奖提案」——
     * 商城兑换没有提案 ID，那条路径下这张表根本插不进去。详见实体类的字段注释。
     */
    private String sourceBizId;

    /** 来源类型：PROPOSAL / MALL */
    private String sourceType;

    /*
     * 🔴 下面三处 @Size 不是 UI 层面的挑剔，是<b>密文列宽的硬约束</b>。
     * 三列都加密落库，密文长度 = 3(前缀) + base64(12 + 明文字节数 + 16)：
     *     姓名 40 字符(120B) -> 203  ≤ varchar(255)
     *     电话 30 字符( 30B) ->  83  ≤ varchar(255)
     *     地址 100 字符(300B) -> 443 ≤ varchar(512)
     * 放开上限而不改列宽，后果是 MySQL 非严格模式<b>静默截断密文</b> ——
     * 表现为「存进去了，读出来解密失败」，而且那一行救不回来。
     * 改这里或改列宽时，两边一起改，算式见 PiiCipher.cipherTextLength。
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

}