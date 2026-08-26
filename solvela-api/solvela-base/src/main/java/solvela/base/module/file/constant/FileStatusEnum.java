package solvela.base.module.file.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import solvela.base.enumeration.BaseEnum;

/**
 * 文件生命周期状态。
 *
 * <p><b>没有这个状态会怎样</b>：用户在表单里选了文件（已经上传成功）然后关掉页面没提交 ——
 * 这个文件永远没人引用，也永远不会被删。跑几年后存储里一半是垃圾，
 * 而且<b>你无法区分哪些是垃圾</b>，因为没有引用关系可查。
 *
 * <p>这个约定必须第一天就定好：事后补等于把所有存量文件当成已确认，垃圾永远清不掉。
 *
 * @Date 2026-08-10
 */
@AllArgsConstructor
@Getter
public enum FileStatusEnum implements BaseEnum {

    /**
     * 已上传但业务还没确认引用。超过保留期（默认 7 天）由定时任务清理。
     *
     * <p>窗口取 7 天而不是 24 小时：运营周三传素材、周五配活动、下周一发布是常态，
     * 24 小时会把还没用上的素材清掉，这种事故很难解释。
     */
    TEMP(1, "临时"),

    /**
     * 业务已确认引用，永不自动清理。物理删除前仍需查 t_file_relation 确认无人引用。
     */
    CONFIRMED(2, "已确认"),

    ;

    private final Integer value;

    private final String desc;
}
