package solvela.scriptengine.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 脚本注册表 实体类。
 *
 * <p>🔴 <b>这张表只由 {@code ScriptFileLoader} 写</b>，是 {@code resources/scripts/} 下文件的只读镜像。
 * 没有 create_by / update_by 两列，正是因为不存在「人写这张表」这回事。
 * 手工改 content 不会生效，下次启动就被文件覆盖回去。
 */
@Data
@TableName("t_script")
public class Script {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 脚本唯一编码，由文件路径推导，如 task/streak_sign_7d */
    private String scriptCode;

    /** 脚本名称，来自文件头 @name */
    private String scriptName;

    /** 业务域，对应 ScriptDomain 枚举，由 scene 推导 */
    private String domain;

    /** 场景，对应 ScriptScene 枚举，来自文件头 @scene */
    private String scene;

    /** classpath 下的路径 */
    private String filePath;

    /** 脚本内容（只读镜像） */
    private String content;

    /** content 的 SHA-256 */
    private String contentHash;

    /** 版本号，内容变化时 +1 */
    private Integer version;

    /** 入参契约快照 JSON，由 ScriptScene.getParams() 生成 */
    private String paramsSchema;

    /** 返回值类型 */
    private String returnType;

    /** 用途说明，来自文件头 @desc */
    private String description;

    /** 状态：0-停用, 1-启用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
