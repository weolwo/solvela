package solvela.admin.interceptor;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.strategy.SaAnnotationStrategy;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import solvela.admin.module.system.login.domain.RequestEmployee;
import solvela.admin.module.system.login.service.LoginService;
import solvela.base.annotation.AllowAnonymous;
import solvela.base.code.SystemErrorCode;
import solvela.base.code.UserErrorCode;
import solvela.base.domain.ResponseDTO;
import solvela.base.web.CurrentUser;
import solvela.base.web.SolvelaResponseUtil;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;

/**
 * admin 拦截器
 *
 * @Author 1024创新实验室-主任:卓大
 * @Date 2023/7/26 20:20:33
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>，Since 2012
 */

@Component
@Slf4j
public class AdminInterceptor implements HandlerInterceptor {

    @Resource
    private LoginService loginService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // OPTIONS请求直接return
        if (HttpMethod.OPTIONS.toString().equals(request.getMethod())) {
            response.setStatus(HttpStatus.NO_CONTENT.value());
            return false;
        }

        boolean isHandler = handler instanceof HandlerMethod;
        if (!isHandler) {
            return true;
        }

        try {
            // --------------- 第一步： 根据token 获取用户 ---------------

            String tokenValue = StpUtil.getTokenValue();
            String loginId = (String) StpUtil.getLoginIdByToken(tokenValue);
            RequestEmployee requestEmployee = loginService.getLoginEmployee(loginId, request);

            // --------------- 第二步： 校验 登录 ---------------

            Method method = ((HandlerMethod) handler).getMethod();
            AllowAnonymous allowAnonymous = ((HandlerMethod) handler).getMethodAnnotation(AllowAnonymous.class);
            if (allowAnonymous != null) {
                updateActiveTimeout(requestEmployee);
                CurrentUser.bind(requestEmployee);
                return true;
            }

            if (requestEmployee == null) {
                SolvelaResponseUtil.write(response, ResponseDTO.error(UserErrorCode.LOGIN_STATE_INVALID));
                return false;
            }

            // 更新活跃
            updateActiveTimeout(requestEmployee);


            // --------------- 第三步： 校验 权限 ---------------

            CurrentUser.bind(requestEmployee);
            if (SaAnnotationStrategy.instance.isAnnotationPresent.apply(method, SaIgnore.class)) {
                return true;
            }

            // 如果是超级管理员的话，不需要校验权限
            if (requestEmployee.getAdministratorFlag()) {
                return true;
            }

            SaAnnotationStrategy.instance.checkMethodAnnotation.accept(method);

        } catch (SaTokenException e) {
            /*
             * sa-token 异常状态码
             * 具体请看： https://sa-token.cc/doc.html#/fun/exception-code
             */
            int code = e.getCode();
            if (code == 11041 || code == 11051) {
                SolvelaResponseUtil.write(response, ResponseDTO.error(UserErrorCode.NO_PERMISSION));
            } else if (code == 11016) {
                SolvelaResponseUtil.write(response, ResponseDTO.error(UserErrorCode.LOGIN_ACTIVE_TIMEOUT));
            } else if (code >= 11011 && code <= 11015) {
                SolvelaResponseUtil.write(response, ResponseDTO.error(UserErrorCode.LOGIN_STATE_INVALID));
            } else {
                SolvelaResponseUtil.write(response, ResponseDTO.error(UserErrorCode.PARAM_ERROR));
            }
            return false;
        } catch (Throwable e) {
            SolvelaResponseUtil.write(response, ResponseDTO.error(SystemErrorCode.SYSTEM_ERROR));
            log.error(e.getMessage(), e);
            return false;
        }

        // 通过验证
        return true;
    }


    /**
     * 更新活跃时间
     */
    private void updateActiveTimeout(RequestEmployee requestEmployee) {
        if (requestEmployee == null) {
            return;
        }
        StpUtil.updateLastActiveToNow();
    }


    // 不再需要 afterCompletion 清理上下文：身份绑在 RequestScopeFilter 打开的
    // ScopedValue 作用域里，doFilter 一返回就自动失效，异常路径也一样。
}