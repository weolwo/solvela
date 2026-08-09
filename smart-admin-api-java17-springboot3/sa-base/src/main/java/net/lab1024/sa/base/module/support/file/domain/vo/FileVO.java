package net.lab1024.sa.base.module.support.file.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.swagger.SchemaEnum;
import net.lab1024.sa.base.module.support.file.constant.FileFolderTypeEnum;
import net.lab1024.sa.base.module.support.file.constant.FileStatusEnum;
import net.lab1024.sa.base.module.support.file.constant.FileVisibilityEnum;

import java.time.LocalDateTime;

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
public class FileVO {

    @Schema(description = "主键")
    private Long fileId;

    @Schema(description = "存储文件夹类型")
    @SchemaEnum(FileFolderTypeEnum.class)
    private Integer folderType;

    @Schema(description = "分类ID")
    private Long categoryId;

    /**
     * 对应 DB 的 {@code original_name}（v3.53.0 改名）。
     * <b>属性名刻意不跟着改</b> —— 前端有 5 个文件在用 fileName / fileKey，
     * JSON 契约的变更属于档⑤，SQL 里已做别名。
     */
    @Schema(description = "文件名称（用户上传时的原名）")
    private String fileName;

    /**
     * v3.53.0 起 DB 列是 bigint。原来是 Integer，2GB 以上会静默溢出。
     */
    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    @Schema(description = "文件类型")
    private String fileType;

    @Schema(description = "扩展名（从嗅探MIME反推）")
    private String extension;

    @Schema(description = "真实MIME")
    private String contentType;

    /**
     * 对应 DB 的 {@code storage_key}（v3.53.0 改名），见上面 fileName 的说明。
     */
    @Schema(description = "存储键")
    private String fileKey;

    @Schema(description = "存储介质：LOCAL / S3")
    private String storageKind;

    @Schema(description = "可见性")
    @SchemaEnum(FileVisibilityEnum.class)
    private Integer visibility;

    @Schema(description = "生命周期状态")
    @SchemaEnum(FileStatusEnum.class)
    private Integer status;

    @Schema(description = "标签，前后各带逗号")
    private String tags;

    @Schema(description = "上传人")
    private Long creatorId;

    @Schema(description = "上传人")
    private String creatorName;

    @Schema(description = "创建人（用户名）")
    private String createBy;

    @SchemaEnum(value = UserTypeEnum.class, desc = "创建人类型")
    private Integer creatorUserType;

    @Schema(description = "文件展示url")
    private String fileUrl;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
