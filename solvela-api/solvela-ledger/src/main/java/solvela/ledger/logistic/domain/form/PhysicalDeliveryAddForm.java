package solvela.ledger.logistic.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发货物流表 新建表单
 *
 * @Author weolwo
 * @Date 2026-04-19 00:03:01
 * @Copyright weolwo
 */

@Data
public class PhysicalDeliveryAddForm {

    /**
     * 会员号 —— 关联键。调用方只需给它，账号快照由服务端查会员表补
     * （见 {@code MemberService.requireMemberName}），这样快照与会员号<b>不可能对不上</b>。
     */
    @Schema(description = "会员号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "会员号 不能为空")
    private Long memberId;

    /**
     * 来源单号。原先这里叫 proposalId(Long)，写死了「履约单必来自发奖提案」——
     * 商城兑换没有提案 ID，那条路径下这张表根本插不进去。详见实体类的字段注释。
     */
    @Schema(description = "来源单号：PROPOSAL 存提案ID / MALL 存订单号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "来源单号 不能为空")
    @Size(max = 64, message = "来源单号 最多 64 位")
    private String sourceBizId;

    @Schema(description = "来源类型：PROPOSAL / MALL", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "来源类型 不能为空")
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
    @Schema(description = "收件人姓名（密文落库）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "收件人姓名 不能为空")
    @Size(max = 40, message = "收件人姓名 最多 40 个字")
    private String receiverName;

    @Schema(description = "收件人电话（密文落库）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "收件人电话 不能为空")
    @Size(max = 30, message = "收件人电话 最多 30 位")
    private String receiverPhone;

    @Schema(description = "收件详细地址（密文落库）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "收件详细地址 不能为空")
    @Size(max = 100, message = "收件详细地址 最多 100 个字")
    private String receiverAddress;

    @Schema(description = "物流公司")
    private String logisticsCompany;

    @Schema(description = "物流单号")
    private String logisticsNo;

}