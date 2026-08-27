package solvela.member.verify.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实名信息详情 VO —— <b>这是唯一会下发姓名与身份证明文的地方</b>。
 *
 * <p>为什么要和列表 VO 分开：审核一条实名认证，运营必须看到完整的姓名和证件号
 * （核对真伪就是这个页面存在的理由）。但那是<b>一次看一条</b>的动作，
 * 和「列表一屏铺开几十条」是完全不同的暴露面。分成两个接口之后，
 * 「谁能看到完整证件号」就变成了一个可以单独授权、单独审计的动作。
 *
 * <p>加密（PiiTypeHandler）防的是静态泄露，脱敏防的是一屏看到几十个，
 * 而接口拆分防的是「本来只想看列表却顺手拿到了全量明文」。三件事各解决一层。
 *
 * @Date 2026-08-23
 */
@Data
public class MemberVerifyDetailDTO {

    /** id */
    private Long id;

    /** 会员号 */
    private Long memberId;

    /** 账号 */
    private String memberName;

    /** 昵称 */
    private String nickname;

    /** 真实姓名（明文） */
    private String realName;

    /** 身份证号（明文） */
    private String idCard;

    /** 认证状态：0-未认证, 1-认证中, 2-已认证, 3-认证失败 */
    private Integer verifyStatus;

    /** 认证通过时间 */
    private LocalDateTime verifyTime;

    /** 认证失败原因 */
    private String failReason;

    /** 提交时间 */
    private LocalDateTime createTime;
}
