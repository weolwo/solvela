package solvela.member.api;

/**
 * 手机号 + 密码认证的入参。
 *
 * <h3>为什么 clientIp / deviceType 在这里，traceId 不在</h3>
 * 前两个是<b>要落库的数据</b>（{@code t_member_login_log} 的 client_ip / device_type 两列），
 * 域服务拿它们来写日志，属于业务参数。而且拆成独立服务后，只有网关知道真实的客户端 IP，
 * 走请求头还得额外解决「下游凭什么信这个头」。
 *
 * <p>traceId 则相反：它对每个接口都一样，塞进每个 Cmd 是噪音，还会因为某个调用点忘了填而静默变空。
 * 它走 MDC（{@code solvela.base.trace.Trace}）—— 今天是同进程的 ThreadLocal，
 * 拆分后由服务端 Filter 把 {@code traceId} 请求头放进 MDC，域里那行读取代码两种场景都对。
 *
 * <h3>deviceId 与 deviceType 是两回事</h3>
 * deviceType 是客户端<b>自报</b>的端（可以撒谎，但只能在几个已知取值里撒）；
 * deviceId 是服务端<b>签发并验签</b>过的设备号 —— 网关那一层已经确认它是我们发的，
 * 所以域里可以拿它做限流判断。反过来 deviceType 只配用于展示与分类。
 *
 * <p>允许为 null：灰度期间老客户端还没带设备令牌（见 {@code DeviceAuthProperties.Mode}）。
 * 🔴 <b>为 null 时不要当成「可疑」处理</b> —— 那会在 enforce 之前就把老版本用户全挡在外面。
 *
 * <h3>identity 与 credential 是什么，取决于 loginType</h3>
 * <pre>
 *   PHONE_PASSWORD   identity=手机号   credential=明文密码
 *   EMAIL_PASSWORD   identity=邮箱     credential=明文密码
 *   EMAIL_CODE       identity=邮箱     credential=邮箱验证码
 * </pre>
 *
 * <p>🔴 <b>字段名刻意是中性的</b>。原来这里叫 {@code phone}，加邮箱通道时最省事的做法是
 * 「继续叫 phone，但有时候放的是邮箱」—— 那种字段名迟早会骗到某个人，
 * 而它骗人的方式是让他写出一段「按手机号规范化一个邮箱」的代码，且不报错。
 *
 * @param loginType  登录方式，决定 identity/credential 怎么解释。<b>调用方显式传，不从格式猜</b>
 * @param identity   手机号或邮箱，任意格式，域内会按 loginType 规范化
 * @param credential 明文密码或邮箱验证码
 * @param deviceType 设备端 APP/H5/WECHAT/PC，为空按 H5 记
 * @param clientIp   客户端 IP，允许为 null
 * @param deviceId   验签通过的设备号，允许为 null（老客户端）
 */
public record MemberAuthCmd(
        MemberLoginType loginType,
        String identity,
        String credential,
        /**
         * <b>二次验证码</b>，仅当这台设备处在观察档时才用得到。
         *
         * <p>与 {@link #credential} 不是一回事：credential 是「你知道什么」
         *（密码，或 EMAIL_CODE 登录时的那个码），这一项是「这台设备最近可疑，
         * 再证明一次你能收到本人的短信/邮件」。
         *
         * <p>正常设备上<b>永远为 null</b> —— 客户端不必先问一次「要不要验」，
         * 而是先不带地提交，被服务端回 {@code DEVICE_VERIFICATION_REQUIRED}
         * 之后再补。多一次往返，换的是绝大多数登录不受影响。
         */
        String verificationCode,
        String deviceType,
        String clientIp,
        String deviceId,

        /**
         * 本次请求<b>已经核销过</b>一张有效的滑块通行票。
         *
         * <h3>🔴 为什么是「已核销」而不是「票在这里」</h3>
         * 票的核销是一次 Redis 的「取出即删」，只能发生一次 —— 那一步归接入层（它才认得请求头）。
         * 域要的只是结论。把票原文传下来的话，域就得去碰 Redis 里属于接入层的键，
         * 而「一张票只放行一次请求」这件事也会变成两处都能做、谁都不确定谁做过。
         *
         * <h3>为什么要由域来判「要不要滑块」</h3>
         * 改造前这件事写在网关：密码登录一律先拦。于是「熟悉网络免滑块」「全局滑块开关」
         * 这类判断没有地方落 —— 它们要看账号画像和规则表，那都在域里。
         *
         * <h3>🔴 是 Boolean 而不是 boolean</h3>
         * 本命令会<b>经 JSON 跨进程</b>（网关 → app-biz 的内部接口）。用原始类型的话，
         * 任何不带这个字段的请求体都会被 Jackson 挡在反序列化阶段，报的是
         * 「Cannot map null into type boolean」—— 一个 400，而且和业务一点关系都没有。
         * 老版本网关、内部工具、手写的联调请求都会撞上它。null 一律当作 false（没过滑块），
         * 往严的方向偏。
         */
        Boolean captchaPassed) {

    /** 兼容既有调用点的手机号密码登录。 */
    public static MemberAuthCmd byPhonePassword(String phone, String password,
                                                String deviceType, String clientIp, String deviceId) {
        return new MemberAuthCmd(MemberLoginType.PHONE_PASSWORD, phone, password, null,
                deviceType, clientIp, deviceId, false);
    }
}
