package solvela.member.api;

/**
 * 手机号 + 密码注册的入参。
 *
 * <h3>为什么没有账号和昵称</h3>
 * {@code t_member.member_name}（微信号风格，字母开头 6~20 位）与 {@code nickname}
 * 都由域自动生成，用户注册后自己改 —— 与微信同一个做法，DDL 注释里那句
 * 「用户可改」说的就是这件事。
 *
 * <p>让用户在注册时填账号，代价是多一轮「这个账号已被占用」的往返，
 * 而此刻他要的只是「进去」。
 *
 * <h3>clientIp 是必需的，不是可选的</h3>
 * 与 {@link MemberAuthCmd} 不同：那边 IP 只用来写登录日志，缺了无非少一列；
 * 这边 IP 同时是<b>限频的键</b>（见 {@link RegisterFailReason#TOO_MANY_ATTEMPTS}）
 * 与 {@code t_member.register_ip}（批量注册的识别依据）。
 * 拿不到 IP 时域会按「限不住」处理并打日志，而不是静默放行。
 *
 * @param phone          用户输入的手机号，任意格式，域内会经 {@code MemberPhoneUtil} 规范化
 * @param password       用户输入的明文密码，强度校验在域内
 * @param deviceType     设备端 APP/H5/WECHAT/PC，为空按 H5 记
 * @param clientIp       客户端 IP，落 register_ip 并作为限频键
 * @param registerSource 注册来源渠道，落 {@code t_member.register_source}；为空按 UNKNOWN 记
 */
public record MemberRegisterCmd(
        String phone,
        String password,
        String deviceType,
        String clientIp,
        String registerSource) {
}
