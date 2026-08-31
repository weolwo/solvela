package solvela.scriptengine.domain;

import lombok.Data;

import java.util.List;

/**
 * 吐给前端编辑器的场景文档。
 *
 * <p>编辑器拿到它之后，除了函数补全，还能补全<b>当前场景可用的变量</b> ——
 * 在此之前变量名全靠运营记，写错了要等脚本跑起来才知道。
 */
@Data
public class ScriptSceneDocDTO {

    /**
     * 场景枚举名，等于 t_script.scene 的取值，如 TASK_RULE
     */
    private String scene;

    /**
     * 场景中文名，如「任务完成判定」
     */
    private String title;

    /**
     * 所属业务域枚举名
     */
    private String domain;

    /**
     * 所属业务域中文名
     */
    private String domainTitle;

    /**
     * 用途说明
     */
    private String description;

    /**
     * 必须返回的类型简名，如 Boolean
     */
    private String returnType;

    /**
     * 本场景脚本可用的变量
     */
    private List<Param> params;

    /**
     * 这个场景<b>现在挂得上去吗</b>：是否存在「接受本场景 + 引擎已接入」的挂载点。
     *
     * <p>false 表示脚本写出来也没地方挂、挂了也不会执行。
     * 🔴 而场景<b>创建后不可改</b>，所以这件事必须在<b>写脚本之前</b>就告诉人，
     * 不能等他写完保存完、到挂载那一步看见一个灰掉的下拉才发现 —— 那时只能换个编码重建。
     */
    private Boolean mountable;

    @Data
    public static class Param {

        /**
         * 变量名，脚本里直接引用
         */
        private String name;

        /**
         * 类型简名
         */
        private String type;

        /**
         * 是否必填
         */
        private Boolean required;

        /**
         * 用途说明
         */
        private String description;

        /**
         * 补全展示串，如 memberId: Long
         */
        private String signature;
    }
}
