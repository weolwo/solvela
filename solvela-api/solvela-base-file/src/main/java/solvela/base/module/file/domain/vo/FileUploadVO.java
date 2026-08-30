package solvela.base.module.file.domain.vo;

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

    /** 文件id */
    private Long fileId;

    /** 文件名称 */
    private String originalName;

    /** fileUrl */
    private String fileUrl;

    /** storageKey */
    private String storageKey;

    /** 文件大小 */
    private Long fileSize;

    /** 扩展名 */
    private String extension;
}
