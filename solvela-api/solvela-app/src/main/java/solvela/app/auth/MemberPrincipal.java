package solvela.app.auth;

import java.io.Serializable;

/**
 * 当前请求的会员身份。
 *
 * <p><b>刻意不复用管理端的 {@code RequestEmployee}</b>。那个类是按后台员工的形状定的 ——
 * 员工有工号、有角色、有数据范围、有 userType，于是「用户」这个抽象里就带着这些概念。
 * 会员一个都没有：
 * 会员的授权边界是「只能动自己的数据」，由每个 service 用 memberId 过滤，
 * 不存在角色，也不存在「用户类型」这个维度（本进程里只可能是会员）。
 *
 * <p>让会员去实现员工的接口，换来的是「两边能塞进同一个变量」，
 * 代价是每加一个后台概念都要想一遍会员怎么办 —— 这笔买卖不划算。
 * 两个端各有各的身份类型，各有各的上下文，谁也不迁就谁。
 *
 * <p>用 record 是因为身份在一次请求内<b>不该被改</b>：它会进日志、进审计、
 * 决定数据可见性。可变的身份对象意味着「这个值到了这一行还是不是登录时那个」
 * 要靠读全链路才能回答。
 *
 * <h3>这里没有手机号，是刻意的</h3>
 * 本对象会被缓存进 Redis、每个请求反序列化一次、被 toString 打进日志。
 * 放明文手机号进来，等于让整套 PII 加密（{@code PiiCipher} / {@code PiiHasher}）失效。
 * 需要展示手机号的页面走单独接口，用 {@code MemberPhoneUtil.mask} 脱敏后下发。
 *
 * @param memberId     会员号，全链路关联键
 * @param memberName   账号（用户可改，但唯一）—— 留痕用它，不用昵称
 * @param nickname     昵称，展示用
 * @param avatarFileId 头像 file_id
 * @param gender       性别：0-未知, 1-男, 2-女
 */
public record MemberPrincipal(
        Long memberId,
        String memberName,
        String nickname,
        Long avatarFileId,
        Integer gender) implements Serializable {
}
