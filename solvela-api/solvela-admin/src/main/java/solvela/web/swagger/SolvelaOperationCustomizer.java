package solvela.web.swagger;

import io.swagger.v3.oas.models.Operation;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;
import solvela.web.AllowAnonymous;
import solvela.web.RequiresPermission;

/**
 * 在接口文档上标出这个接口的鉴权要求。
 *
 * <p>只有三种可能，所以只写三行：免登录、需要登录、需要某个权限点。
 *
 * <p>原先这里读的是 sa-token 的 {@code @SaCheckPermission} / {@code @SaCheckRole}，
 * 还带一段把 {@code SaMode.AND/OR} 渲染成「且 / 或」的逻辑。两处问题一并去掉了：
 * <ul>
 *   <li>「角色校验」那一段读的其实是 {@code SaCheckPermission}（复制粘贴时漏改），
 *       于是同一个权限点会以「角色」的名义再打印一遍 —— 文档上一直是错的；</li>
 *   <li>{@code @SaCheckRole} 全项目零使用，那套「且 / 或」的渲染没有任何输入。</li>
 * </ul>
 *
 * @Author 1024创新实验室-主任:卓大
 * @Date 2023/12/26 13:47:39
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
public class SolvelaOperationCustomizer implements OperationCustomizer {

    private static final String RED = "<font style=\"color:red\" class=\"light-red\">";

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        operation.setDescription(describeAuth(handlerMethod));
        return operation;
    }

    private String describeAuth(HandlerMethod handlerMethod) {
        if (handlerMethod.hasMethodAnnotation(AllowAnonymous.class)) {
            return RED + "鉴权：免登录，公网可直接访问</font>";
        }
        RequiresPermission required = handlerMethod.getMethodAnnotation(RequiresPermission.class);
        if (required == null) {
            return RED + "鉴权：需要登录</font>";
        }
        return RED + "鉴权：需要权限点 " + required.value() + "</font>";
    }
}
