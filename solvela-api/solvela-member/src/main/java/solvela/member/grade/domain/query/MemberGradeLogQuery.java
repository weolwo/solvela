package solvela.member.grade.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import solvela.base.domain.PageParam;

import java.time.LocalDate;

/**
 * 等级变更留痕分页查询。
 *
 * <p>⚠️ 这里的 {@code memberId} <b>可以为空</b>，与成长值流水相反 ——
 * 「最近一段时间谁被人工调级了」是一个真实的审计场景，
 * 而等级变更是低频事件，全表按时间倒序翻页不会出问题。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MemberGradeLogQuery extends PageParam {

    private Long memberId;

    /** 账号，模糊匹配 */
    private String memberName;

    /** 见 {@code GradeChangeType}：UPGRADE / DOWNGRADE / KEEP / MANUAL / RISK_REVOKE */
    private String changeType;

    /** 操作人。只想看某个员工调过谁的等级时用 */
    private String operator;

    private LocalDate createTimeBegin;

    private LocalDate createTimeEnd;
}
