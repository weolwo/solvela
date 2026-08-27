package solvela.base.module.file.domain.vo;

import lombok.Data;
import solvela.base.swagger.SchemaEnum;
import solvela.base.module.file.constant.FileStatusEnum;

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

    /** 主键 */
    private Long fileId;

    /** 分类ID */
    private Long categoryId;

    /** 用户上传时的原始文件名 */
    private String originalName;

    /**
     * v3.53.0 起 DB 列是 bigint。原来是 Integer，2GB 以上会静默溢出。
     */
    private Long fileSize;

    /** 扩展名（从嗅探MIME反推） */
    private String extension;

    /** 真实MIME */
    private String contentType;

    /** 存储键，系统生成、不可变 */
    private String storageKey;

    /** 存储介质：LOCAL / S3 */
    private String storageKind;

    /** 生命周期状态 */
    @SchemaEnum(FileStatusEnum.class)
    private Integer status;

    /** 标签，前后各带逗号 */
    private String tags;

    /** 创建人（用户名） */
    private String createBy;

    /** 文件展示url */
    private String fileUrl;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 被多少个业务引用着。列表页直接给出来，运营扫一眼就知道哪些图在用、哪些是可以清理的
     * —— 否则要逐个点开详情抽屉才看得到。
     *
     * <p>用相关子查询一次算完，不在循环里逐个查（{@code idx_file} 正好覆盖）。
     */
    private Integer referenceCount;
}
