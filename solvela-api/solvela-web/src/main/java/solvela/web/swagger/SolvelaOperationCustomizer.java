package solvela.web.swagger;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import io.swagger.v3.oas.models.Operation;
import solvela.base.util.SolvelaStringUtil;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限、接口加解密等
 *
 * @Author 1024创新实验室-主任:卓大
 * @Date 2023/12/26 13:47:39
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */

public class SolvelaOperationCustomizer implements OperationCustomizer {

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {

        List<String> noteList = new ArrayList<>();

        /*
         * 这里原先还会读 @ApiDecrypt / @ApiEncrypt，在文档上标一行红字
         * 「接口安全：【请求参数加密】」。2026-08-25 apiencrypt 模块移交 solvela-admin 之后删除 ——
         * solvela-base 不能反过来依赖 solvela-admin，而这两个注解已经在那边了。
         *
         * 只丢了一个<b>装饰性的文档标记</b>，加解密本身照常工作（那是 advice 干的事）。
         * 真想找回这行标记，让 solvela-admin 自己再注册一个 OperationCustomizer 即可 ——
         * 不值得为它把整个 apiencrypt 拽回 solvela-base。
         */

        // 权限
        noteList.addAll(getPermission(handlerMethod));

        // 更新
        operation.setDescription(SolvelaStringUtil.join("<br/>", noteList));

        return operation;
    }


    private List<String> getPermission(HandlerMethod handlerMethod) {
        List<String> values = new ArrayList<>();

        StringBuilder permissionStringBuilder = new StringBuilder();
        SaCheckPermission classPermissions = handlerMethod.getBeanType().getAnnotation(SaCheckPermission.class);
        if (classPermissions != null) {
            permissionStringBuilder.append("<font style=\"color:red\" class=\"light-red\">");
            permissionStringBuilder.append("类：").append(getAnnotationNote(classPermissions.value(), classPermissions.mode()));
            permissionStringBuilder.append("</font></br>");
        }

        SaCheckPermission methodPermission = handlerMethod.getMethodAnnotation(SaCheckPermission.class);
        if (methodPermission != null) {
            permissionStringBuilder.append("<font style=\"color:red\" class=\"light-red\">");
            permissionStringBuilder.append("方法：").append(getAnnotationNote(methodPermission.value(), methodPermission.mode()));
            permissionStringBuilder.append("</font></br>");
        }

        if (permissionStringBuilder.length() > 0) {
            permissionStringBuilder.insert(0, "<font style=\"color:red\" class=\"light-red\">权限校验：</font></br>");
            values.add(permissionStringBuilder.toString());
        }


        StringBuilder roleStringBuilder = new StringBuilder();
        SaCheckRole classCheckRole = handlerMethod.getBeanType().getAnnotation(SaCheckRole.class);
        if (classCheckRole != null) {
            roleStringBuilder.append("<font style=\"color:red\" class=\"light-red\">");
            roleStringBuilder.append("类：").append(getAnnotationNote(classCheckRole.value(), classCheckRole.mode()));
            roleStringBuilder.append("</font></br>");
        }

        SaCheckPermission methodCheckRole = handlerMethod.getMethodAnnotation(SaCheckPermission.class);
        if (methodCheckRole != null) {
            roleStringBuilder.append("<font style=\"color:red\" class=\"light-red\">");
            roleStringBuilder.append("方法：").append(getAnnotationNote(methodCheckRole.value(), methodCheckRole.mode()));
            roleStringBuilder.append("</font></br>");
        }

        if (roleStringBuilder.length() > 0) {
            roleStringBuilder.insert(0, "<font style=\"color:red\" class=\"light-red\">角色校验：</font></br>");
            values.add(roleStringBuilder.toString());
        }

        return values;
    }

    private String getAnnotationNote(String[] values, SaMode mode) {
        if (mode.equals(SaMode.AND)) {
            return String.join(" 且 ", values);
        } else {
            return String.join(" 或 ", values);
        }
    }
}
