package solvela.scriptengine;

import solvela.enums.EnableStatusEnum;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 脚本引用关系 实体类。
 *
 * <p>这张表存在的唯一理由：回答<b>「改这个脚本会影响哪些业务对象」</b>。
 * 设计取舍时以能不能干净地回答这个问题为准。
 */
@Data
@TableName("t_script_ref")
public class ScriptRef {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 引用的脚本编码，关联 t_script.script_code
     */
    private String scriptCode;

    /**
     * 引用方类型，见 ScriptRefPoint 枚举
     */
    private String refType;

    /**
     * 引用方业务编码（template_code / pool_code / activity_code）
     */
    private String refId;

    /**
     * 挂载槽位，见 ScriptRefPoint 枚举
     */
    private String refSlot;

    /**
     * 状态：0-停用, 1-启用
     */
    private EnableStatusEnum status;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
