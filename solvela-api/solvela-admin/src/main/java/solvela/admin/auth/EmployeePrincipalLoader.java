package solvela.admin.auth;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import solvela.admin.module.system.login.domain.RequestEmployee;
import solvela.admin.module.system.login.manager.LoginManager;
import solvela.base.constant.RequestHeaderConst;
import solvela.web.SolvelaServletUtil;

/**
 * 员工号 → 可用的登录身份。<b>「什么算一个可用身份」只在这里定义。</b>
 *
 * <p>把禁用判断收在这里而不是散在调用点，是因为它是一条安全判据：
 * 少判一处就是一个「已禁用的账号还能调这个接口」的口子。
 * 令牌吊销（{@code revokeAll}）是主防线，这里是第二道 ——
 * 主防线依赖「禁用时记得调吊销」，而这一道不依赖任何人记得做什么。
 */
@Component
@RequiredArgsConstructor
public class EmployeePrincipalLoader {

    private final LoginManager loginManager;

    /**
     * @return 可用身份；员工不存在或已禁用时返回 null
     */
    public RequestEmployee load(Long employeeId, HttpServletRequest request) {
        RequestEmployee employee = loginManager.getRequestEmployee(employeeId);
        if (employee == null || Boolean.TRUE.equals(employee.getDisabledFlag())) {
            return null;
        }
        // ip 与 user-agent 是<b>本次请求</b>的属性，不能进缓存 ——
        // 缓存里带上它们，操作日志会把所有人的 ip 都记成第一个把缓存捂热的那个人
        employee.setIp(SolvelaServletUtil.getClientIP(request));
        employee.setUserAgent(request.getHeader(RequestHeaderConst.USER_AGENT));
        return employee;
    }
}
