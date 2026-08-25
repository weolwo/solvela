package solarx.app.module.login.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import sa.base.common.domain.RequestUser;
import sa.base.common.enumeration.UserTypeEnum;

import java.io.Serializable;

/**
 * 当前请求的会员身份，由 {@code MemberInterceptor} 放进 {@code SmartRequestUtil} 的 ThreadLocal。
 *
 * <p>对应管理端的 {@code RequestEmployee}，但<b>没有权限相关字段</b>：会员没有角色、
 * 没有菜单、没有数据范围 —— C 端的授权边界是「只能操作自己的数据」，靠每个 service
 * 用 {@code memberId} 过滤，不是靠权限点。哪天真出现「会员等级决定能不能进某个活动」，
 * 那是业务规则，写在业务里，不要在这里长出一个 permission 列表。
 *
 * <h3>🔴 这里刻意不放手机号、邮箱等 PII</h3>
 * 本对象会被<b>缓存进 Redis</b>（见 {@code MemberLoginManager}），每个请求都会反序列化一次。
 * 把手机号放进来意味着：明文手机号常驻缓存、进日志（对象被 toString 时）、
 * 也很容易被顺手塞进某个返回给前端的 VO 里 —— 一整套 PII 加密就白做了。
 * 需要展示手机号的页面走单独接口，用 {@code MemberPhoneUtil.mask} 脱敏后下发。
 *
 * @Date 2026-08-25
 */
@Data
public class RequestMember implements RequestUser, Serializable {

    @Schema(description = "会员号")
    private Long memberId;

    @Schema(description = "账号（微信号风格，用户可改）")
    private String memberName;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像 file_id")
    private Long avatarFileId;

    @Schema(description = "性别：0-未知, 1-男, 2-女")
    private Integer gender;

    @Schema(description = "状态：1-正常, 2-冻结, 3-已注销")
    private Integer status;

    @Schema(description = "请求ip")
    private String ip;

    @Schema(description = "请求 user-agent")
    private String userAgent;

    /**
     * 🔴 用 {@code memberId} 而不是自增主键：全链路关联键就是它，
     * 落到 {@code t_member_wallet}、{@code t_mall_order} 等表里的也是它。
     */
    @Override
    public Long getUserId() {
        return memberId;
    }

    /**
     * 🔴 返回 {@code memberName}（账号）而不是 {@code nickname}。
     * {@code RequestUser#getUserName} 的结果会被写进操作日志、数据变更留痕这类
     * <b>事后追溯</b>的地方，而昵称是随时可改、且不唯一的 —— 用它留痕等于没留。
     */
    @Override
    public String getUserName() {
        return memberName;
    }

    @Override
    public UserTypeEnum getUserType() {
        return UserTypeEnum.MEMBER;
    }
}
