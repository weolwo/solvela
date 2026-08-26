package solvela.member.loginlog.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 会员登录日志统计。
 *
 * <p>与列表<b>用同一套查询条件</b>：顶部筛选改了统计跟着变。两套条件的话，
 * 运营会看到「统计说今天 100 次登录，下面列表只有 3 条」，然后不知道该信哪个。
 *
 * @Date 2026-08-23
 */
@Data
public class MemberLoginLogStatDTO {

    @Schema(description = "登录记录总数（含失败与登出）")
    private Long totalCount;

    @Schema(description = "成功次数")
    private Long successCount;

    @Schema(description = "失败次数")
    private Long failCount;

    /**
     * 独立会员数。<b>这才是「有多少人登录」</b> —— totalCount 是「登录了多少次」，
     * 一个人刷 20 次会让它严重虚高，拿它当活跃人数用会把日活算错一个量级。
     */
    @Schema(description = "独立会员数（去重）")
    private Long memberCount;

    @Schema(description = "登录成功的独立会员数（去重）")
    private Long successMemberCount;

    @Schema(description = "独立IP数（去重）：批量注册/撞库的第一眼信号")
    private Long ipCount;
}
