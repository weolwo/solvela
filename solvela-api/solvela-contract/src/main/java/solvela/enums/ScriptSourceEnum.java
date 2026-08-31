package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 脚本版本的来源。
 *
 * <p>两种来源的<b>权威性完全一样</b> —— 都是 {@code t_script} 里的一行，都能被激活。
 * 这一列只回答「这一版是怎么进来的」，供排查用，不参与任何生效判断。
 *
 * <h3>为什么不是「文件优先」</h3>
 * 曾经是。启动时扫描 {@code resources/scripts/} 覆盖写库，于是「谁是权威」由部署时机决定，
 * 后台改一行脚本会在下次发版时被静默覆盖回去。现在运行期只认 {@code active_flag = 1} 的那一行，
 * 文件退回到<b>开发工作区</b>的位置：写在那里、走 git review，上线靠人复制进后台并激活。
 *
 * @Author alaric
 * @Date 2026-08-31
 */
@Getter
@AllArgsConstructor
public enum ScriptSourceEnum implements BaseEnum {

    /**
     * 由项目文件导入。历史数据与一次性种子导入用，日常不会产生新的 FILE 版本。
     */
    FILE("FILE", "项目文件"),

    /**
     * 后台录入。这是现在唯一的常规来源。
     */
    MANUAL("MANUAL", "后台录入"),
    ;

    private final String value;

    private final String desc;
}
