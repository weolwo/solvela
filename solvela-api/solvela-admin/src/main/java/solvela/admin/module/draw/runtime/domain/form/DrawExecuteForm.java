package solvela.admin.module.draw.runtime.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 执行抽奖 表单（后台联调用）。
 *
 * <p>⚠️ 原注释写着「memberId 联调期显式传入；C端接入后改为从登录态获取并删除该字段」——
 * 那条已被推翻：同一个引擎会被 C 端、后台补发、定时任务三种调用方调用，
 * 后两者没有登录态。会员号必须显式传，见契约方案 §3.1。
 *
 * @Author alaric
 * @Date 2026-07-26
 */
@Data
public class DrawExecuteForm {

    @Schema(description = "活动编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "活动编码 不能为空")
    private String activityCode;

    @Schema(description = "奖池编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "奖池编码 不能为空")
    private String poolCode;

    /**
     * 会员号（关联键）。v3.71.0 之前这里收的是账号 —— 账号可改，改完这个人的
     * 限领计数、白名单、历史流水就全对不上了，而且不报错。
     * 展示用的账号由服务端查会员表取，调用方不需要也不应该再传。
     */
    @Schema(description = "会员号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "会员号 不能为空")
    private Long memberId;

    @Schema(description = "请求ID：传入则启用幂等防重。重复提交会拿回第一次的完整结果")
    private String requestId;

    /**
     * 抽几次。不传按 1 次。
     *
     * <p>🔴 引擎<b>不校验上限</b>（由上游保证）—— 联调时填个大数就是真的抽那么多次，
     * 会把奖池抽空，也会在一个事务里产生成千上万次 Redis + DB 往返。
     */
    @Schema(description = "抽几次，不传按 1 次")
    @Min(value = 1, message = "抽奖次数至少 1 次")
    private Integer times;
}
