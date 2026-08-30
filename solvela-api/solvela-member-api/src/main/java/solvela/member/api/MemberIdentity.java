package solvela.member.api;

import solvela.enums.GenderEnum;

/**
 * 会员的<b>可用身份</b>：认证链路要用的那几个字段，仅此而已。
 *
 * <p>能拿到这个对象，就意味着「这个会员现在可以正常使用」——
 * 状态判断已经在 {@code MemberAuthService} 里做完了，调用方不需要也拿不到 status。
 * 「什么算一个可用身份」由会员域回答，不外泄给每个调用方各判一遍。
 *
 * <h3>这里没有手机号，也没有密码</h3>
 * 本对象会被网关缓存进 Redis、每个请求反序列化一次、被 toString 打进日志。
 * 放明文手机号进来等于让整套 PII 加密失效；密码哈希更是一步都不该出会员域。
 *
 * @param memberId     会员号，全链路关联键
 * @param memberName   账号（用户可改，但唯一）—— 留痕用它，不用昵称
 * @param nickname     昵称，展示用
 * @param avatarFileId 头像 file_id
 * @param gender       性别
 */
public record MemberIdentity(
        Long memberId,
        String memberName,
        String nickname,
        Long avatarFileId,
        GenderEnum gender) {
}
