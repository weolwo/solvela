package solvela.base.module.support.redis;

import solvela.base.common.util.SolvelaStringUtil;
import org.redisson.config.Config;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.stereotype.Component;

/**
 *
 * redission对于password 为空处理有问题，重新设置下
 *
 * @Author 1024创新实验室-主任:卓大
 * @Date 2024/7/16 01:04:18
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a> ，Since 2012
 */

@Component
public class RedissonPasswordConfigurationCustomizer implements RedissonAutoConfigurationCustomizer {
    @Override
    public void customize(Config configuration) {
        // ==========================================
        // 1. 替换序列化器，消灭 JDK 25 的 Kryo Unsafe 警告
        // ==========================================
        // 原 Kryo5Codec 依赖 sun.misc.Unsafe，在 JDK 22+ 已废弃。
        // 改用 Jackson 既安全又方便在 Redis 客户端查看明文 JSON 数据。
        // 将默认的 Kryo 编码器替换为 Jackson (JSON格式，安全且不依赖 Unsafe)
        //config.setCodec(new JsonJacksonCodec());

        // 既抛弃了危险的 Kryo，又比纯 JSON 更省内存和网络。
        // ⚠️ 用 SmileJackson3Codec 而不是 SmileJacksonCodec：后者是 Jackson 2 的实现，
        // 而 Spring Boot 4 全线已是 Jackson 3（tools.jackson），用旧的等于在运行包里
        // 多养一整套 Jackson 2（2.36MB）。Smile 是格式规范，两代实现产出的字节流一致。
        configuration.setCodec(new org.redisson.codec.SmileJackson3Codec());

        // 2. 修复原代码因为调用废弃 API 导致无限打印警告的 Bug
        // ==========================================
        // 提取当前的密码配置 (兼容新老版本的读取方式)
        String pwd = configuration.getPassword();
        if (pwd == null) {
            if (configuration.isSingleConfig()) {
                pwd = configuration.useSingleServer().getPassword();
            } else if (configuration.isClusterConfig()) {
                pwd = configuration.useClusterServers().getPassword();
            } else if (configuration.isSentinelConfig()) {
                pwd = configuration.useSentinelServers().getPassword();
            }
        }

        // 核心：保留卓大的初衷（空串转 null），但使用顶层的 configuration.setPassword() 设置。
        // 绝对不要再去调用 useSingleServer().setPassword()，那个废弃方法只要一碰就会打警告日志！
        if (SolvelaStringUtil.isEmpty(pwd)) {
            configuration.setPassword(null);
        } else {
            configuration.setPassword(pwd);
        }
    }
}
