package solvela.base.module.support.file.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 修改文件的展示层信息。
 *
 * <p><b>刻意只开放这两个字段</b>：storageKey / contentType / extension / fileSize 都是
 * 上传那一刻由系统定的事实，不该被后台改。尤其是 storageKey ——
 * 它不可变是 CDN 能设 immutable、以及"换图不用刷缓存"的前提。
 *
 * @Date 2026-08-10
 */
@Data
public class FileMetaUpdateForm {

    @Schema(description = "文件ID")
    @NotNull(message = "文件ID不能为空")
    private Long fileId;

    @Schema(description = "文件名称（展示用，不影响存储）")
    private String originalName;

    /**
     * 标签列表。后端会拼成 {@code ,双十一,banner,} 的存储形式 ——
     * 前后带逗号是为了让检索能精确匹配，否则搜「618」会命中「6180」。
     * 含逗号的标签会被丢弃而不是转义：那会破坏存储结构。
     */
    @Schema(description = "标签列表")
    private List<String> tags;
}
