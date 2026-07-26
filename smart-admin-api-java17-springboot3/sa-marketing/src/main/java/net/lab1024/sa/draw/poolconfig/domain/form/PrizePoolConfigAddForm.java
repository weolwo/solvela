package net.lab1024.sa.draw.poolconfig.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import net.lab1024.sa.base.common.util.SmartCodeUtil;

/**
 * 奖池配置 新建表单
 *
 * @Author weolwo
 * @Date 2026-04-19 09:42:12
 * @Copyright weolwo
 */

@Data
public class PrizePoolConfigAddForm {

    @Schema(description = "租户id，不传落库取默认值 '0'")
    private String tenantId;

    @Schema(description = "归属活动编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "活动编码 不能为空")
    @Pattern(regexp = SmartCodeUtil.BIZ_CODE_REGEX, message = "活动" + SmartCodeUtil.BIZ_CODE_MESSAGE)
    private String activityCode;

    @Schema(description = "奖池编码：10 位大写字母+数字，全局唯一", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "奖池编码 不能为空")
    @Pattern(regexp = SmartCodeUtil.BIZ_CODE_REGEX, message = "奖池" + SmartCodeUtil.BIZ_CODE_MESSAGE)
    private String poolCode;

    @Schema(description = "奖池名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "奖池名称 不能为空")
    private String poolName;

    @Schema(description = "消耗资产类型: CREDIT(积分), TICKET(抽奖券), NONE(无消耗)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "消耗资产类型: CREDIT(积分), TICKET(抽奖券), NONE(无消耗) 不能为空")
    private String costAssetType;

    @Schema(description = "消耗数值(单价)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "消耗数值(单价) 不能为空")
    private BigDecimal costValue;

    @Schema(description = "重置周期，天，周，月，活动期间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "重置周期，天，周，月，活动期间 不能为空")
    private String resetPeriod;

    @Schema(description = "抽奖算法: 1-按概率(probability), 2-按库存比例(stock_ratio)")
    private Integer drawMode;

    @Schema(description = "进入该奖池的前置脚本")
    private String scriptId;

    @Schema(description = "0关闭，1开启")
    private Integer status;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}