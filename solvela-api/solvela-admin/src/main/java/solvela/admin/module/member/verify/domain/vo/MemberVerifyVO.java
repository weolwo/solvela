package solvela.admin.module.member.verify.domain.vo;

import solvela.enums.MemberVerifyStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实名信息列表 VO。
 *
 * <p>🔴 <b>姓名与身份证在这里是脱敏值，不是明文</b>。列表一屏几十行，下发明文等于
 * 一次截图就是一批身份证号泄露 —— 而列表的用途只是「看有哪些待审、认出是谁」，不需要完整号码。
 * 审核那一条时才需要完整信息，走 {@code MemberVerifyDetailDTO}（单独接口、单独权限）。
 *
 * <p>也<b>没有 idCardHash</b>：它是 binary(32) 的内部查重字段，映射成 String 是乱码，
 * 下发它还等于把「这个身份证有没有注册过」的判断能力交给前端。
 *
 * @Author weolwo
 * @Date 2026-08-22 21:00:09
 * @Copyright weolwo
 */
@Data
public class MemberVerifyVO {

    @Schema(description = "id")
    private Long id;

    @Schema(description = "会员号")
    private Long memberId;

    /**
     * join 出来的展示字段：只显示 10 位会员号的话，运营认不出这是谁
     */
    @Schema(description = "账号")
    private String memberName;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "真实姓名（已脱敏：张*丰）")
    private String realName;

    @Schema(description = "身份证号（已脱敏：330102********1234）")
    private String idCard;

    @Schema(description = "认证状态：0-未认证, 1-认证中, 2-已认证, 3-认证失败")
    private MemberVerifyStatusEnum verifyStatus;

    @Schema(description = "认证通过时间")
    private LocalDateTime verifyTime;

    @Schema(description = "认证失败原因")
    private String failReason;

    @Schema(description = "提交时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
