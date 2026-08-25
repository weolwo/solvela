package solvela.admin.module.system.job.api.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 已注册的定时任务执行器，供后台下拉框选择。
 *
 * <p>让运营从列表里<b>选</b>而不是手打一个类名 —— 手打正是「配了却永远不跑」的温床。
 *
 * @author alaric
 * @date 2026-08-11
 */
@Data
public class SolvelaJobHandlerVO {

    @Schema(description = "执行器名称，存进 t_solvela_job.handler_name")
    private String handlerName;

    @Schema(description = "中文标题")
    private String title;

    @Schema(description = "分组")
    private String groupName;

    @Schema(description = "执行车道：FAST/SLOW。由执行器声明，运营不可改")
    private String lane;

    @Schema(description = "是否幂等：只有幂等的执行器才允许配置失败重试")
    private Boolean idempotent;

    @Schema(description = "业务日期偏移天数：统计类通常为 -1")
    private Integer bizDateOffset;

    @Schema(description = "默认超时秒数，0 表示不限")
    private Integer defaultTimeoutSeconds;

    @Schema(description = "真实实现类，仅供排查展示，不参与任何匹配")
    private String handlerClassName;

    /**
     * 参数声明，供后台渲染表单 —— 替掉「运营在文本框里手填 JSON」
     */
    @Schema(description = "参数声明列表")
    private java.util.List<ParamSchema> paramSchemaList;

    @lombok.Data
    public static class ParamSchema {

        @Schema(description = "参数键名")
        private String key;

        @Schema(description = "中文说明，作为表单项 label")
        private String desc;

        @Schema(description = "类型：STRING/INT/BOOLEAN/DATE/ENUM")
        private String type;

        @Schema(description = "是否必填")
        private Boolean required;

        @Schema(description = "默认值")
        private String defaultValue;

        @Schema(description = "ENUM 类型的候选项")
        private java.util.List<String> options;
    }
}
