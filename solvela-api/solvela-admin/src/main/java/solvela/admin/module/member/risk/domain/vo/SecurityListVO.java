package solvela.admin.module.member.risk.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import solvela.enums.SecurityListDimensionEnum;
import solvela.enums.SecurityListStatusEnum;
import solvela.enums.SecurityListTypeEnum;

import java.time.LocalDateTime;

/**
 * 名单的一行。
 *
 * <h3>🔴 身份摘要原样给出，不还原成明文</h3>
 * 摘要是单向的，还原不了 —— 这正是它的用途（{@code PiiHasher}）。
 * 页面上显示一串 hex 看着不友好，但「列表页能读出所有被封用户的手机号」是更糟的事。
 * 客服要确认是谁，用查询框输明文反查（服务端算摘要再匹配）。
 */
@Data
@Schema(description = "安全名单")
public class SecurityListVO {

    private Long id;

    @Schema(description = "名单类型：1-黑, 2-灰, 3-白")
    private SecurityListTypeEnum listType;

    @Schema(description = "维度：1-IP, 2-IP段, 3-身份摘要, 4-会员")
    private SecurityListDimensionEnum dimension;

    @Schema(description = "IP / CIDR / 身份摘要（hex）/ 会员号")
    private String listValue;

    @Schema(description = "灰名单命中加的分；黑白名单为空")
    private Integer score;

    private String reason;

    @Schema(description = "来源：1-人工, 2-自动")
    private Integer source;

    @Schema(description = "状态：1-有效, 0-已撤销")
    private SecurityListStatusEnum status;

    @Schema(description = "到期时间。空表示长期（只有 IP 维度允许）")
    private LocalDateTime expireTime;

    private String operator;

    private String revokeOperator;

    private String revokeReason;

    private LocalDateTime revokeTime;

    private LocalDateTime createTime;
}
