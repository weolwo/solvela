package solvela.admin;

import solvela.base.listener.Ip2RegionListener;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Solvela 项目启动类
 *
 * @Author 1024创新实验室-主任:卓大
 * @Date 2022-08-29 21:00:58
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@EnableCaching
@EnableScheduling
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
@ComponentScan(AdminApplication.COMPONENT_SCAN)
@ConfigurationPropertiesScan("solvela.admin.auth")
@MapperScan(value = AdminApplication.COMPONENT_SCAN, annotationClass = Mapper.class)
// Spring Boot 4 把 UserDetailsServiceAutoConfiguration 挪进了独立的 spring-boot-security 模块，
// 本项目只依赖 spring-security-crypto（没有 security starter），该自动配置根本不在 classpath 上，
// 排除项已无意义 —— 而且 Boot 对 exclude 里不存在的类会直接启动失败，必须删掉。
@SpringBootApplication
public class AdminApplication {

    public static final String COMPONENT_SCAN = "solvela";

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(AdminApplication.class);
        application.addListeners(new Ip2RegionListener());
        application.run(args);
    }
}
