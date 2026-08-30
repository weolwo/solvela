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
 * @param phone      用户输入的手机号，任意格式，域内会规范化
 * @param password   用户输入的明文密码
 * @param deviceType 设备端 APP/H5/WECHAT/PC，为空按 H5 记
 * @param clientIp   客户端 IP，允许为 null
 */
public record MemberAuthCmd(String phone, String password, String deviceType, String clientIp) {
}
