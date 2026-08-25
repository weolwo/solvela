package solvela.admin.config;

import lombok.extern.slf4j.Slf4j;
import solvela.admin.module.system.repeatsubmit.DefaultMd5KeyGenerator;
import solvela.admin.module.system.repeatsubmit.RepeatSubmitKeyGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class RepeatSubmitConfig {

    //<editor-fold desc="怎么注入自己的生成器">
    /*
    @Slf4j
    @Component // 👈 极其关键！加上这个，让 Spring 第一时间扫到你！
    public class MyVipKeyGenerator implements RepeatSubmitKeyGenerator {

        public MyVipKeyGenerator() {
            log.info("💥 卧槽！成功劫持！Spring 优先实例化了我自己写的 [VIP防重策略]！");
        }

        @Override
        public String generate(JoinPoint point, HttpServletRequest request) {
            String ip = request.getRemoteAddr();
            String url = request.getRequestURI();
            String customKey = "VIP_LIMIT:" + ip + ":" + url;
            return customKey;
        }
    }
     */
    //</editor-fold>
    @Bean
    @ConditionalOnMissingBean(RepeatSubmitKeyGenerator.class)
    public RepeatSubmitKeyGenerator getDefaultKeyGenerator(){
        log.info("🥱 容器里没有自定义策略，正在加载默认的 [MD5防重策略] 作为保底...");
        return new DefaultMd5KeyGenerator();
    }
}
