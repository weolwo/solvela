package solvela.scriptengine.domain.dto;

import lombok.Data;
import solvela.enums.ScriptSourceEnum;

import java.time.LocalDateTime;

/**
 * 脚本版本 VO。
 *
 * <p>一个实例 = {@code t_script} 的一行 = 一个版本。列表页按 {@code scriptCode} 分组，
 * 每组里 {@code active = true} 的那一个才是线上正在跑的。
 */
@Data
public class ScriptDTO {

    /** 版本行 id。激活、查看内容都用它定位，脚本编码定位不到具体版本 */
    private Long id;

    /** 脚本编码。同一编码有多行，每行一个版本 */
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

    /** 来源文件路径，仅 source=FILE 的行有值 */
    private String filePath;

    /** 这一版是怎么进来的 */
    private ScriptSourceEnum source;

    /** 这一版改了什么 */
    private String changeLog;

    /** 脚本内容（只读） */
    private String content;

    /** 版本号，同一脚本编码下从 1 递增 */
    private Integer version;

    /** 是不是当前生效的那一版。同一 scriptCode 下至多一个 true */
    private Boolean active;

    /** 该脚本编码被多少个业务对象引用。<b>按编码统计，不分版本</b> —— 引用挂的是编码 */
    private Integer refCount;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
