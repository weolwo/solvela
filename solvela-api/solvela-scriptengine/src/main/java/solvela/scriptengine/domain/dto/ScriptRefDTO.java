package solvela.scriptengine.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 脚本引用关系 VO
 */
@Data
public class ScriptRefDTO {

    /** id */
    private Long id;

    /** 脚本编码 */
    private String scriptCode;

    /** 脚本名称 */
    private String scriptName;

    /** 挂载点枚举名，如 PRIZE_POOL_ENTRY */
    private String refPoint;

    /** 挂载点中文名，如「奖池 - 准入判定」 */
    private String refPointTitle;

    /** 引用方类型 */
    private String refType;

    /** 引用方业务编码 */
    private String refId;

    /** 挂载槽位 */
    private String refSlot;

    /** 状态：0-停用, 1-启用 */
    private Integer status;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
