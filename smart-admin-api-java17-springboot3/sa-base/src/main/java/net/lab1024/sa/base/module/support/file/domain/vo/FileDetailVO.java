package net.lab1024.sa.base.module.support.file.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 文件详情：基础信息 + <b>被谁引用着</b>。
 *
 * <p>引用列表是这个 VO 存在的主要理由。{@code t_file_relation} 的 {@code idx_file} 索引
 * 一直是为反查建的，但在此之前没有任何界面暴露它 ——
 * 运营删图前看一眼「这张图正在 3 个活动里用」，比删完了才发现活动页变叉强得多。
 *
 * @Date 2026-08-10
 */
@Data
public class FileDetailVO {

    @Schema(description = "文件基础信息")
    private FileVO file;

    @Schema(description = "引用它的业务对象")
    private List<Reference> references;

    /**
     * 一条引用。
     *
     * <p>刻意<b>不去 join 出业务对象的名字</b>：bizType 是开放的字符串，今天是活动、
     * 明天是商品，后端没法穷举所有业务表去取标题。前端按 bizType 映射成中文即可，
     * 需要跳转时也由前端按 bizType 决定路由。
     */
    @Data
    public static class Reference {

        @Schema(description = "业务类型：ACTIVITY_DISPLAY / NOTICE / ...")
        private String bizType;

        @Schema(description = "业务对象ID")
        private Long bizId;
    }
}
