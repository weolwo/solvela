package solvela.member.verify.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员实名列表的<b>读模型</b>：mapper 的 resultMap 映射到这里，service 也返回它。
 *
 * <p>DTO 是领域能查出来的全部，VO 是某个端决定给出去的那一部分，装配在端上做。
 * 完整说明见 {@code MemberWalletDTO}。
 */
@Data
public class MemberVerifyDTO {

    private Long id;

    /** 会员号 */
    private Long memberId;

    /**
     * join 出来的展示字段：只显示 10 位会员号的话，运营认不出这是谁
     */
    private String memberName;

    /** 昵称 */
    private String nickname;

    /** 真实姓名（已脱敏：张*丰） */
    private String realName;

    /** 身份证号（已脱敏：330102********1234） */
    private String idCard;

    /** 认证状态：0-未认证, 1-认证中, 2-已认证, 3-认证失败 */
    private Integer verifyStatus;

    /** 认证通过时间 */
    private LocalDateTime verifyTime;

    /** 认证失败原因 */
    private String failReason;

    /** 提交时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
