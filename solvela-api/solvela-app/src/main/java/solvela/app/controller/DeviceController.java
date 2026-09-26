package solvela.app.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.app.auth.Anonymous;
import solvela.app.auth.CurrentDevice;
import solvela.app.auth.RequestCredentials;
import solvela.app.auth.DeviceExempt;
import solvela.app.domain.DeviceRegisterRequest;
import solvela.app.domain.DeviceRegisterView;
import solvela.app.service.DeviceRegisterService;
import solvela.app.web.ClientIp;

/**
 * 设备身份。客户端<b>首次启动时</b>调一次，不是首次登录。
 *
 * <h3>为什么是启动而不是登录</h3>
 * 匿名接口（注册、登录、活动页）也要有设备身份 —— 而防刷要防的恰恰是它们。
 * 等到登录才领，等于把最需要保护的那几条路留在外面。
 *
 * <h3>两个注解都要标，缺一不可</h3>
 * <ul>
 *   <li>{@link Anonymous}：这时候还没有会员；</li>
 *   <li>{@link DeviceExempt}：这时候还没有设备 —— 要求它带设备令牌就成了
 *       先有鸡还是先有蛋。</li>
 * </ul>
 * 两个注解是正交的两件事，见 {@code DeviceExempt} 的类注释。
 */
@Tag(name = "设备身份")
@RestController
@RequestMapping("/device")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceRegisterService deviceRegisterService;

    private final RequestCredentials credentials;

    /**
     * 领一个设备身份。
     *
     * <h3>已经有身份的请求：复用，不新建</h3>
     * 请求带着一个验签通过的设备令牌（请求头或 cookie）时，直接复用它 —— 同一台设备调多少次都是同一台。
     * Web 端据此可以<b>每次启动都调一次</b>：有 cookie 就只是续期，没有才新建。
     * 以前这里每次都新建，防重复全靠客户端的内存去重，多标签页并发时一个人会凭空多出几台设备。
     *
     * <p>迁移也走这条路：前端把 localStorage 里的旧令牌放进请求头调一次，
     * 服务端把<b>同一个</b>令牌写进 cookie —— 设备号不断档，「这台设备碰过哪些号」的历史还在。
     *
     * <h3>没有身份：新建</h3>
     * 服务端无从判断「这是不是同一台设备」（能判断的前提是客户端能自证身份），
     * 所以新建不幂等，异常量由会员域的 IP 限频兜住。
     *
     * <h3>cookie 模式（Web）：令牌只写进 HttpOnly cookie，响应体里不带</h3>
     * 理由同登录接口：响应体里有一份，混进页面的脚本就能截走它。
     *
     * <p>IP 在端上取，不传进 service —— 理由同 {@code MemberLoginController.login}。
     */
    @Anonymous
    @DeviceExempt
    @PostMapping("/register")
    public DeviceRegisterView register(@RequestBody @Valid DeviceRegisterRequest request,
                                       HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        DeviceRegisterView view = CurrentDevice.find()
                .map(identity -> new DeviceRegisterView(credentials.deviceValue(servletRequest), identity.deviceId()))
                .orElseGet(() -> deviceRegisterService.register(request, ClientIp.of(servletRequest)));
        if (!request.cookieDelivery()) {
            return view;
        }
        credentials.writeDevice(servletResponse, view.deviceToken());
        return new DeviceRegisterView(null, view.deviceId());
    }
}
