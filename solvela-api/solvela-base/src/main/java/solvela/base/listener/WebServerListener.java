package solvela.base.listener;

import lombok.extern.slf4j.Slf4j;
import solvela.code.ErrorCodeRegister;
import solvela.base.enumeration.SystemEnvironmentEnum;
import solvela.base.util.SolvelaEnumUtil;
import solvela.base.util.SolvelaIpUtil;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/**
 * 启动监听器
 *
 * @Author 1024创新实验室: 罗伊
 * @Date 2021-12-23 23:45:26
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
@Slf4j
@Component
@Order(value = 1024)
public class WebServerListener implements ApplicationListener<WebServerInitializedEvent> {

    @Override
    public void onApplicationEvent(WebServerInitializedEvent webServerInitializedEvent) {
        // 项目信息
        showProjectMessage(webServerInitializedEvent);
    }

    /**
     * 显示项目信息
     */
    private void showProjectMessage(WebServerInitializedEvent webServerInitializedEvent) {
        WebServerApplicationContext context = webServerInitializedEvent.getApplicationContext();
        Environment env = context.getEnvironment();

        //获取服务信息
        String ip = SolvelaIpUtil.getLocalFirstIp();
        Integer port = webServerInitializedEvent.getWebServer().getPort();
        String contextPath = env.getProperty("server.servlet.context-path");
        if (contextPath == null) {
            contextPath = "";
        }
        String profile = env.getProperty("spring.profiles.active");
        SystemEnvironmentEnum environmentEnum = SolvelaEnumUtil.getEnumByValue(profile, SystemEnvironmentEnum.class);
        String projectName = env.getProperty("project.name");
        //拼接服务地址
        String title = String.format("-------------【%s】 服务已成功启动 （%s started successfully）-------------", projectName, projectName);

        // 初始化状态码
        int codeCount = ErrorCodeRegister.initialize();
        String localhostUrl = normalizeUrl(String.format("http://localhost:%d%s", port, contextPath));
        String externalUrl = normalizeUrl(String.format("http://%s:%d%s", ip, port, contextPath));
        // 文档地址只在 springdoc 真正装配时才打印。
        // 生产包里整套 springdoc / swagger 已被排除（见 solvela-admin/pom.xml 的 prod profile），
        // 照旧打印这两行会让运维以为文档还开着，跑去访问又是一片报错。
        boolean docEnabled = ClassUtils.isPresent("org.springdoc.core.customizers.OperationCustomizer",
                WebServerListener.class.getClassLoader());
        String docLines = "";
        if (docEnabled) {
            String swaggerUrl = normalizeUrl(String.format("http://localhost:%d%s/swagger-ui/index.html", port, contextPath));
            String knife4jUrl = normalizeUrl(String.format("http://localhost:%d%s/doc.html", port, contextPath));
            docLines = String.format("%n\tSwagger地址:\t%s%n\tknife4j地址:\t%s", swaggerUrl, knife4jUrl);
        }
        log.warn("\n{}\n" +
                        "\t当前启动环境:\t{} , {}" +
                        "\n\t返回码初始化:\t完成{}个返回码初始化" +
                        "\n\t服务本机地址:\t{}" +
                        "\n\t服务外网地址:\t{}" +
                        "{}" +
                        "\n-------------------------------------------------------------------------------------\n",
                title, profile, environmentEnum.getDesc(), codeCount, localhostUrl, externalUrl, docLines);
    }

    /**
     * 把 path 里连着的斜杠压成一条，只为启动日志好看。
     * context-path 配成 "/" 或 "/api/" 时拼出来就是 http://host:8080//doc.html，能访问但很像笔误。
     * 注意别把 "http://" 里那对斜杠也压掉。
     */
    private String normalizeUrl(String url) {
        int schemeEnd = url.indexOf("://");
        if (schemeEnd < 0) {
            return url.replaceAll("/{2,}", "/");
        }
        int pathStart = schemeEnd + 3;
        return url.substring(0, pathStart) + url.substring(pathStart).replaceAll("/{2,}", "/");
    }

}