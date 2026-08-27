package solvela.scriptengine.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 脚本 VO。后台只读展示用 —— 脚本权威在文件，不提供编辑接口。
 */
@Data
public class ScriptDTO {

    /** 脚本编码，由文件路径推导 */
    private String scriptCode;

    /** 脚本名称 */
    private String scriptName;

    /** 业务域枚举名 */
    private String domain;

    /** 业务域中文名 */
    private String domainTitle;

    /** 场景枚举名 */
    private String scene;

    /** 场景中文名 */
    private String sceneTitle;

    /** 返回值类型 */
    private String returnType;

    /** 用途说明 */
    private String description;

    /** classpath 下的文件路径 */
    private String filePath;

    /** 脚本内容（只读） */
    private String content;

    /** 版本号，内容每变一次 +1 */
    private Integer version;

    /** 被多少个业务对象引用 */
    private Integer refCount;

    /** 状态：0-停用(文件已删除), 1-启用 */
    private Integer status;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
