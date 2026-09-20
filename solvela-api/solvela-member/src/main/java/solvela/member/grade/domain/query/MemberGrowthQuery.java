package solvela.member.grade.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import solvela.base.domain.PageParam;

/**
 * 会员成长值分页查询的<b>领域参数</b>。
 *
 * <p>分层理由见 {@code MemberLoginLogQuery}：Form 跟着某个端的页面走，
 * Query 跟着领域能力走。这里刻意没有 {@code @Schema} 与校验注解。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MemberGrowthQuery extends PageParam {

    /** 会员号：精确匹配 */
    private Long memberId;

    /**
     * 账号，模糊匹配。
     *
     * <p>运营嘴里说得出口的是账号，不是 10 位会员号 —— 只给 memberId 的话，
     * 每次查等级都得先去会员列表翻一次号码。
     */
    private String memberName;

    /** 等级下限（含）。配合上限查「金卡及以上」这类区间 */
    private Integer gradeMin;

    /** 等级上限（含） */
    private Integer gradeMax;

    /**
     * 只看在保级缓冲期内的人。
     *
     * <p>这是运营最想单独捞出来的一群：他们正处在「可能掉级」的窗口里，
     * 是做召回和推送的第一批目标。阶段 4 有了缓冲期之后这个筛选才有数据。
     */
    private Boolean inProtect;
}
