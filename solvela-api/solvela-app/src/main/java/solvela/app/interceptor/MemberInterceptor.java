package solvela.app.interceptor;

import cn.dev33.satoken.exception.SaTokenException;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import solvela.base.annotation.AllowAnonymous;
import solvela.code.SystemErrorCode;
import solvela.code.UserErrorCode;
import solvela.base.domain.ResponseDTO;
import solvela.base.web.CurrentUser;
import solvela.web.SolvelaResponseUtil;
import solvela.app.config.StpMemberUtil;
import solvela.app.module.login.domain.RequestMember;
import solvela.app.module.login.service.MemberLoginService;

/**
 * 会员端拦截器：解析 token、还原登录态、拦住未登录请求。
 *
 * <h3>与管理端 AdminInterceptor 的关键差异</h3>
 * <ol>
 *   <li>用 {@link StpMemberUtil} 而不是 {@code StpUtil} —— 两个进程共用 Redis，
 *       共用默认 loginType 会让员工 token 通过会员端校验，理由见 StpMemberUtil 类注释；</li>
 *   <li><b>没有权限校验</b>。会员没有角色和权限点，C 端的授权边界是「只能动自己的数据」，
 *       由各 service 用 {@code memberId} 过滤 —— 这件事拦截器做不了，也不该假装做得了。
 *       🔴 所以写 C 端接口时：<b>凡是带 id 的操作，都要验这条数据属不属于当前会员</b>，
 *       不要因为「拦截器已经拦过了」就省掉这一步，那拦的只是「有没有登录」。</li>
 * </ol>
 *
 * @Date 2026-08-25
 */
@Slf4j
@Component
public class MemberInterceptor implements HandlerInterceptor {

    @Resource
    private MemberLoginService memberLoginService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        // 预检请求直接放行，不走鉴权（浏览器不会给 OPTIONS 带上自定义头）
        if (HttpMethod.OPTIONS.toString().equals(request.getMethod())) {
            response.setStatus(HttpStatus.NO_CONTENT.value());
            return false;
        }

        // 静态资源、错误页等非 controller 方法
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        try {
            String tokenValue = StpMemberUtil.getTokenValue();
            RequestMember requestMember = null;
            if (tokenValue != null) {
                String loginId = (String) StpMemberUtil.getLoginIdByToken(tokenValue);
                requestMember = memberLoginService.getLoginMember(loginId, request);
            }

            // 🔴 免登录接口也要把已登录用户的身份放进上下文：
            // 活动详情这类接口「登录与否都能看，但登录了要额外返回我的参与记录」，
            // 如果这里直接 return true，接口里就永远拿不到当前会员，只能再写一套解析。
            if (handlerMethod.getMethodAnnotation(AllowAnonymous.class) != null) {
                setContext(requestMember);
                return true;
            }

            if (requestMember == null) {
                SolvelaResponseUtil.write(response, ResponseDTO.error(UserErrorCode.LOGIN_STATE_INVALID));
                return false;
            }

            setContext(requestMember);
            return true;

        } catch (SaTokenException e) {
            // sa-token 异常码表：https://sa-token.cc/doc.html#/fun/exception-code
            // 会员端没有权限模型，所以不处理 11041/11051（无权限）那两档 —— 走到这里
            // 只可能是 token 本身的问题。
            int code = e.getCode();
            if (code == 11016) {
                SolvelaResponseUtil.write(response, ResponseDTO.error(UserErrorCode.LOGIN_ACTIVE_TIMEOUT));
            } else if (code >= 11011 && code <= 11015) {
                SolvelaResponseUtil.write(response, ResponseDTO.error(UserErrorCode.LOGIN_STATE_INVALID));
            } else {
                // 其余一律按「登录态无效」处理而不是 500：token 相关的异常
                // 让前端跳登录页是对的，弹「系统错误」只会让用户反复重试
                log.warn("会员端 sa-token 异常, code: {}", code, e);
                SolvelaResponseUtil.write(response, ResponseDTO.error(UserErrorCode.LOGIN_STATE_INVALID));
            }
            return false;
        } catch (Throwable e) {
            SolvelaResponseUtil.write(response, ResponseDTO.error(SystemErrorCode.SYSTEM_ERROR));
            log.error(e.getMessage(), e);
            return false;
        }
    }

    private void setContext(RequestMember requestMember) {
        if (requestMember == null) {
            return;
        }
        CurrentUser.bind(requestMember);
        // 有登录态才续期。active-timeout 当前全局是 -1（不冻结），这行现在是空操作，
        // 但哪天会员端要单独设「30 天不活跃就要重新登录」，它就是那个开关生效的地方。
        StpMemberUtil.updateLastActiveToNow();
    }

    // 曾经这里有一个 afterCompletion 手动清理 ThreadLocal，注释写着「不清就会把上一个请求的
    // 会员身份泄漏给下一个请求」。现在身份绑在 RequestScopeFilter 打开的 ScopedValue 作用域里，
    // doFilter 一返回就失效 —— 那个事故类别被结构性地消灭了，没有可以忘记清理的东西。
}
