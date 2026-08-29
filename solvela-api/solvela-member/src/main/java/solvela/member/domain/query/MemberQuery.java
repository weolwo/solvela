package solvela.member.domain.query;

import solvela.enums.MemberStatusEnum;
import solvela.base.domain.PageParam;

import java.time.LocalDate;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会员分页查询的<b>领域参数</b>。形状与管理端的 {@code MemberQuery} 目前一致，
 * 但<b>变更的理由不同</b>：Form 跟着某个端的页面走，Query 跟着领域能力走。
 *
 * <p>分层与取舍的完整说明见 {@code MemberWalletQuery}（全项目第一个改造的样板）。
 * 这里刻意没有 {@code @Schema} 与校验注解 —— 接口文档和参数校验是端的职责。
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class MemberQuery extends PageParam {

    /** 会员号：精确匹配 */
    private Long memberId;

    /**
     * 模糊匹配。列表页那个输入框是「搜索」，精确等于的话运营得把账号一字不差地打出来
     */
    private String memberName;

    /** 昵称：模糊匹配 */
    private String nickname;

    /** 性别：0-未知, 1-男, 2-女 */
    private Integer gender;

    /** 状态：1-正常, 2-冻结(风控/违规), 3-已注销 */
    private MemberStatusEnum status;

    /** 注册来源渠道：H5/APP/WECHAT/INVITE/IMPORT... */
    private String registerSource;

    /** 邀请人memberId：没有邀请体系时恒为空，留着比事后加表便宜 */
    private Long inviteId;

    /** 注册时间 */
    private LocalDate createTimeBegin;

    /** 注册时间 */
    private LocalDate createTimeEnd;

}
