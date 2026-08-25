package solvela.base.module.support.file.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文件信息
 *
 * @Author 1024创新实验室: 罗伊
 * @Date 2019年10月11日 15:34:47
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@Data
public class FileUploadVO {

    @Schema(description = "文件id")
    private Long fileId;

    @Schema(description = "文件名称")
    private String originalName;

    @Schema(description = "fileUrl")
    private String fileUrl;

    @Schema(description = "storageKey")
    private String storageKey;

    @Schema(description = "文件大小")
    private Long fileSize;

    @Schema(description = "扩展名")
    private String extension;
}
