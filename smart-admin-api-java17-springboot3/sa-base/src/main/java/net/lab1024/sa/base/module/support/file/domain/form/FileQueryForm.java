package net.lab1024.sa.base.module.support.file.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import net.lab1024.sa.base.common.domain.PageParam;
import net.lab1024.sa.base.common.swagger.SchemaEnum;
import net.lab1024.sa.base.common.validator.enumeration.CheckEnum;

import java.time.LocalDate;

/**
 * 文件信息查询
 *
 * @Author 1024创新实验室: 罗伊
 * @Date 2019年10月11日 15:34:47
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@Data
public class FileQueryForm extends PageParam {

    @Schema(description = "分类ID")
    private Long categoryId;

    /**
     * 单个标签。查询时会被拼成 {@code ,标签,} 去匹配，避免「618」命中「6180」。
     */
    @Schema(description = "标签")
    private String tag;

    @Schema(description = "文件名词")
    private String originalName;

    @Schema(description = "文件Key")
    private String storageKey;

    @Schema(description = "扩展名")
    private String extension;

    @Schema(description = "创建人（用户名）")
    private String createBy;

    /**
     * 只要图片。给「选图器」用：主视觉字段选中一个 PDF 是纯粹的错误，不该等到 C 端才发现。
     *
     * <p>按 {@code content_type} 前缀筛而不是按扩展名白名单：扩展名是从嗅探出的 MIME 反推的，
     * content_type 才是那次嗅探的原始结论，中间少一层换算就少一处会漂移的地方。
     */
    @Schema(description = "只查图片")
    private Boolean imageOnly;

    @Schema(description = "创建时间")
    private LocalDate createTimeBegin;

    @Schema(description = "创建时间")
    private LocalDate createTimeEnd;

}
