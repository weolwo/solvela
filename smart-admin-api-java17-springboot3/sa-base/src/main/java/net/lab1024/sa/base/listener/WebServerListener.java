package net.lab1024.sa.base.listener;

import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.base.common.code.ErrorCodeRegister;
import net.lab1024.sa.base.common.enumeration.SystemEnvironmentEnum;
import net.lab1024.sa.base.common.util.SmartEnumUtil;
import net.lab1024.sa.base.common.util.SmartIpUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

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

    @Value("${reload.interval-seconds}")
    private Integer intervalSeconds;

    @Override
    public void onApplicationEvent(WebServerInitializedEvent webServerInitializedEvent) {
        WebServerApplicationContext context = webServerInitializedEvent.getApplicationContext();
        // 初始化reload
        initReload(context);
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
        String ip = SmartIpUtil.getLocalFirstIp();
        Integer port = webServerInitializedEvent.getWebServer().getPort();
        String contextPath = env.getProperty("server.servlet.context-path");
        if (contextPath == null) {
            contextPath = "";
        }
        String profile = env.getProperty("spring.profiles.active");
        SystemEnvironmentEnum environmentEnum = SmartEnumUtil.getEnumByValue(profile, SystemEnvironmentEnum.class);
        String projectName = env.getProperty("project.name");
        //拼接服务地址
        String title = String.format("-------------【%s】 服务已成功启动 （%s started successfully）-------------", projectName, projectName);

        // 初始化状态码
        int codeCount = ErrorCodeRegister.initialize();
        String localhostUrl = normalizeUrl(String.format("http://localhost:%d%s", port, contextPath));
        String externalUrl = normalizeUrl(String.format("http://%s:%d%s", ip, port, contextPath));
        String swaggerUrl = normalizeUrl(String.format("http://localhost:%d%s/swagger-ui/index.html", port, contextPath));
        String knife4jUrl = normalizeUrl(String.format("http://localhost:%d%s/doc.html", port, contextPath));
        log.warn("\n{}\n" +
                        "\t当前启动环境:\t{} , {}" +
                        "\n\t返回码初始化:\t完成{}个返回码初始化" +
                        "\n\t服务本机地址:\t{}" +
                        "\n\t服务外网地址:\t{}" +
                        "\n\tSwagger地址:\t{}" +
                        "\n\tknife4j地址:\t{}" +
                        "\n-------------------------------------------------------------------------------------\n",
                title, profile, environmentEnum.getDesc(), codeCount, localhostUrl, externalUrl, swaggerUrl, knife4jUrl);
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

    /**
     * 初始化reload
     */
    private void initReload(WebServerApplicationContext applicationContext) {
//        将applicationContext转换为ConfigurableApplicationContext
//        ConfigurableApplicationContext configurableApplicationContext = (ConfigurableApplicationContext) applicationContext;
//
//
//        //获取BeanFactory
//        DefaultListableBeanFactory defaultListableBeanFactory = (DefaultListableBeanFactory) configurableApplicationContext.getAutowireCapableBeanFactory();
//
//        //动态注册bean
//        SmartReloadManager reloadManager = new SmartReloadManager(applicationContext.getBean(ReloadCommand.class), intervalSeconds);
//        defaultListableBeanFactory.registerSingleton("smartReloadManager", reloadManager);
    }
}