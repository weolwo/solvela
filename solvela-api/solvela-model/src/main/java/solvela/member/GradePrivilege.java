package solvela.member;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import solvela.enums.EnableStatusEnum;

import java.time.LocalDateTime;

/**
 * 等级权益 —— <b>纯展示，不驱动任何逻辑</b>。
 *
 * <h3>🔴 拆成一张表不等于建了权益中心</h3>
 * 真正的权益靠三样东西实现，一样都没变：
 * <ul>
 *   <li>高等级专享任务 → {@code t_task_config.target_audience = GRADE_GTE_N}</li>
 *   <li>专享活动 / 奖池 → 脚本里 {@code member_gradeAtLeast(n)}</li>
 *   <li>等级价 / 专享商品 → 商城价格模型（还没做）</li>
 * </ul>
 * 这张表回答的是另一个问题：<b>用户在等级页看见自己在保什么</b>。
 * 没有它，用户没有任何理由去保级 —— 而保级正是这套机制要换的活跃与粘性。
 *
 * <h3>⚠️ 谁都不要给它加「生效引擎」</h3>
 * 那一天来临时，应该是<b>新立一个权益域</b>，而不是让这张展示表长出执行语义。
 * 展示与执行混在一张表里之后，改一句文案就有可能改掉一条业务规则。
 *
 * <h3>为什么不是 {@code t_member_grade} 上的一列 varchar</h3>
 * 图标要 file_id、跳转要 url、排序要独立字段，以后还要多语言。
 * 这些塞进一个 {@code varchar(500)} 就是历史包袱本身 —— 那时再拆，
 * 得一边拆一边解析已经写进去的自由文本。
 *
 * @author alaric
 * @date 2026-09-20
 */
@Data
@TableName("t_grade_privilege")
public class GradePrivilege {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 等级值，关联 {@code t_member_grade.grade_code}。
     *
     * <p>⚠️ 关联的是<b>业务键</b>不是自增 id —— 自增 id 换环境会变，
     * 而这张表是要跟着基线一起灌进新环境的。
     */
    private Integer gradeCode;

    /**
     * 权益编码。{@code EXCLUSIVE_TASK / EXCLUSIVE_POOL / BIRTHDAY_GIFT / …}
     *
     * <p>🔴 它是<b>文档</b>：没有任何引擎读它。写成编码而不是自由文本，
     * 只是为了让「白金和钻石都有生日礼」这件事在数据里看得出来。
     */
    private String privilegeCode;

    /** 权益名，直接展示给用户 */
    private String privilegeName;

    /** 权益说明，等级页的第二行小字 */
    private String description;

    private Long iconFileId;

    /** 点进去跳哪儿。为空表示纯展示、不可点 */
    private String actionUrl;

    /** 展示顺序，越大越靠前 */
    private Integer sort;

    private EnableStatusEnum status;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
