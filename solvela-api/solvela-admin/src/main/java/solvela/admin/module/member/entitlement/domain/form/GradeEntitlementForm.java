package solvela.admin.module.member.entitlement.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 权益配置 新增/编辑表单。
 *
 * <p>⚠️ 这里只做「格式对不对」的校验。「类型认不认识」「天数能不能是 0」
 * 这类<b>业务约束在 {@code GradeEntitlementAdminService}</b>。
 *
 * <p>🔴 没有 {@code entitlementCode}：编码是这条配置的身份，由服务端生成。
 * 让前端填就会撞，而撞了之后的表现是保存失败 —— 运营不知道该填什么才不撞。
 *
 * @author alaric
 * @date 2026-09-22
 */
@Data
public class GradeEntitlementForm {

    @Schema(description = "id。新增时不传")
    private Long id;

    @Schema(description = "权益名，会显示给用户", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请填写权益名")
    @Size(max = 64, message = "权益名最多 64 个字")
    private String entitlementName;

    /**
     * 🔴 建成之后<b>不可修改</b>（服务端会忽略编辑时传来的值）。
     *
     * <p>改类型 = 改周期键的口径（{@code yyyy} ↔ {@code yyyyMM}），
     * 而周期键是幂等键的一半：已发过的记录用旧口径，新口径算出来的键与它们不冲突，
     * 于是<b>同一个周期会再发一次</b>。想换周期就新建一条，把旧的停用。
     */
    @Schema(description = "类型：BIRTHDAY-生日礼 / MONTHLY-月度券。建成后不可改",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请选择权益类型")
    private String entitlementType;

    @Schema(description = "需要的最低等级；判据是 >=", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请填写最低等级")
    @Min(value = 0, message = "最低等级不能为负")
    private Integer minGrade;

    @Schema(description = "资产类型：COUPON / BALANCE / SCORE", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请选择资产类型")
    private String assetType;

    @Schema(description = "COUPON 填券模板编码；BALANCE 填面额来源标识")
    @Size(max = 64, message = "资产引用最多 64 个字符")
    private String assetRef;

    @Schema(description = "展示名，会直接显示给用户", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请填写展示名")
    @Size(max = 128, message = "展示名最多 128 个字")
    private String assetName;

    @Schema(description = "发几份", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请填写发放份数")
    @Min(value = 1, message = "发放份数至少为 1")
    private Integer quantity;

    @Schema(description = "BALANCE 的单份面额")
    private BigDecimal amount;

    @Schema(description = "生成后多少天内可领；必须大于 0", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请填写可领取天数")
    @Min(value = 1, message = "可领取天数必须大于 0，否则生成当天就会过期")
    private Integer claimDays;

    @Schema(description = "备注")
    @Size(max = 255, message = "备注最多 255 个字")
    private String remark;
}
