package net.lab1024.sa.activity.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 活动上下线表单：单个开关与批量操作共用一个接口。
 *
 * <p>为什么不复用 {@link ActivityConfigUpdateForm}：那个表单要求活动名称、起止时间等一整套字段，
 * 而列表页上「点一下开关」只想改一个 status。让调用方把整行数据回传一遍，
 * 既冗余又给了「顺手改掉别的字段」的机会 —— 上下线是个独立动作，就该有独立入口。
 *
 * @Author weolwo
 * @Date 2026-07-29
 */
@Data
public class ActivityStatusUpdateForm {

    @Schema(description = "活动id列表，单个操作也用列表传", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "请至少选择一个活动")
    private List<Long> idList;

    /**
     * 目标状态。
     *
     * <p><b>管理端只有「启用 / 禁用」两态</b>：1-启用、2-禁用。
     *
     * <p>刻意<b>不接受 0</b>。库里的 0 是历史遗留的「未开始」，
     * 而「活动有没有开始」是<b>业务层按起止时间实时算出来的</b>，不是运营在这里拨的开关 ——
     * 让管理端能把状态设回 0，等于凭空造出第三种管理态，还会和业务层算出来的结果打架。
     * 存量的 0 一律按「未启用」展示，运营打开开关即变 1，此后只在 1↔2 之间切换。
     */
    @Schema(description = "目标状态：1-启用, 2-禁用", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "目标状态 不能为空")
    private Integer status;
}
