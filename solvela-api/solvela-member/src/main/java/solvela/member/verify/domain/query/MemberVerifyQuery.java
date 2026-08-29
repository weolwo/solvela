package solvela.member.verify.domain.query;

import solvela.enums.MemberVerifyStatusEnum;
import solvela.base.domain.PageParam;

import java.time.LocalDate;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会员实名分页查询的<b>领域参数</b>。形状与管理端的 {@code MemberVerifyQuery} 目前一致，
 * 但<b>变更的理由不同</b>：Form 跟着某个端的页面走，Query 跟着领域能力走。
 *
 * <p>分层与取舍的完整说明见 {@code MemberWalletQuery}（全项目第一个改造的样板）。
 * 这里刻意没有 {@code @Schema} 与校验注解 —— 接口文档和参数校验是端的职责。
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class MemberVerifyQuery extends PageParam {

    /** 会员号：精确匹配 */
    private Long memberId;

    /**
     * 账号，模糊匹配。
     *
     * <p>这个页面<b>没有按姓名/身份证搜索</b>：那两列在库里是密文，SQL 层没法模糊匹配，
     * 真要按姓名找人只能全表解密再比对。所以检索入口只有账号 / 会员号 / 状态 / 时间。
     */
    private String memberName;

    /** 认证状态：0-未认证, 1-认证中, 2-已认证, 3-认证失败 */
    private MemberVerifyStatusEnum verifyStatus;

    /** 创建时间 */
    private LocalDate createTimeBegin;

    /** 创建时间 */
    private LocalDate createTimeEnd;

}
