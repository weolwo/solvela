package solvela.app.domain;

import jakarta.validation.constraints.NotBlank;

/**
 * 手机号 + 密码注册的入参。
 *
 * <h3>只有三个字段，账号和昵称不在这里</h3>
 * {@code member_name} 与 {@code nickname} 由会员域自动生成，用户注册后自己改 ——
 * 与微信同一个做法，DDL 注释里那句「用户可改」说的就是这件事。
 * 让用户在注册时起账号，要多一轮「已被占用」的往返，而此刻他要的只是进去。
 *
 * <h3>刻意没有 confirmPassword</h3>
 * 「两次密码是否一致」是<b>纯交互问题</b>：用户在同一个表单里打了两遍。
 * 传到服务端再比一次，不会发现任何前端发现不了的问题，只是多一个字段、
 * 多一条服务端错误消息、多一处两边可能不一致的措辞。前端自己拦掉。
 *
 * <h3>刻意没有 registerSource</h3>
 * 让客户端自己声明「我从哪来」等于让它随便填 —— 而 {@code t_member.register_source}
 * 是运营用来分析渠道、风控用来识别批量注册的列，可被任意伪造就没有价值。
 * 由网关按 {@link #deviceType} 推导，见 {@code MemberLoginService.register}。
 *
 * @param phone      手机号。<b>刻意不加 {@code @Pattern}</b> —— 格式校验在
 *                   {@code MemberPhoneUtil.normalize} 里，与登录同一份规则
 * @param password   密码明文，依赖 HTTPS 传输。<b>强度规则也不在这里</b>，
 *                   在会员域的 {@code MemberPasswordPolicy}，只有那一处
 * @param deviceType 设备端：APP / H5 / WECHAT / PC，不传按 H5 记
 */
public record MemberRegisterRequest(
        @NotBlank(message = "请输入手机号") String phone,
        @NotBlank(message = "请输入密码") String password,
        String deviceType) {
}
