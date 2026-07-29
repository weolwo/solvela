package net.lab1024.sa.activity.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 活动下游引用的一条明细，如「奖池 3 个」
 *
 * @Author weolwo
 * @Date 2026-07-29
 */
@Schema(description = "活动下游引用明细")
public record ActivityRefItem(

        @Schema(description = "业务名称，可直接展示，如「奖池」「奖品」") String bizName,

        @Schema(description = "引用数量") long count) {
}
