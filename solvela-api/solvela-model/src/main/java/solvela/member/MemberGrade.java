package solvela.member;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import solvela.enums.EnableStatusEnum;

import java.time.LocalDateTime;

/**
 * 会员等级定义。
 *
 * <h3>🔴 等级是【配置】，不是枚举</h3>
 * 门槛、名称、档数都要能在后台改。写成 Java 枚举的话，运营加一档要发版 ——
 * 而加一档恰恰是这类体系最常见的运营动作。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Data
@TableName("t_member_grade")
public class MemberGrade {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 等级：0 起，数字越大越高。
     *
     * <p>🔴 {@code level=0} 那一行<b>必须存在</b>：新会员落在这儿，
     * 判级函数也拿它当兜底。删了的话一个没攒够任何成长值的人会判不出等级。
     */
    private Integer gradeCode;

    /** 等级名：普通会员 / 银卡 / 金卡 / 白金 / 钻石 */
    private String gradeName;

    /** 周期内成长值门槛（含）。{@code level=0} 必须为 0 */
    private Long threshold;

    /** 等级图标 file_id，走文件模块 */
    private Long iconFileId;

    /*
     * 🔴 benefits 这一列已废弃（2026-09-20）：权益拆到了 t_grade_privilege。
     *    一列 varchar(500) 装不下图标 file_id、跳转 url、排序、多语言，
     *    而运营迟早会要这四样 —— 那时再拆，得一边拆一边解析已经写进去的自由文本。
     *
     *    ⚠️ 别在这里加回一个「权益摘要」字段：那等于把刚拆开的东西又焊回来，
     *    而且摘要和 t_grade_privilege 一定会不同步。
     */

    private EnableStatusEnum status;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
