package sa.util;

import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * SnowflakeInitiator
 *
 * @author Chopper
 * @version v1.0
 * 2022-01-14 14:04
 */
@Component
@Slf4j
@AllArgsConstructor
public class SnowflakeInitiator {

    /**
     * 缓存前缀
     */
    private static final String KEY = "snow_flake_initiator_key";

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 尝试初始化
     *
     * @return
     */
    @PostConstruct
    public void init() {
        Long num = stringRedisTemplate.opsForValue().increment(KEY);
        long dataCenter = num / 32;
        long workedId = num % 32;
        //如果数据中心大于32，则抹除缓存，从头开始
        if (dataCenter >= 32) {
            stringRedisTemplate.delete(KEY);
            num = stringRedisTemplate.opsForValue().increment(KEY);
            dataCenter = num / 32;
            workedId = num % 32;
        }
        SnowFlake.initialize(workedId, dataCenter);
    }

    public static void main(String[] args) {
        SnowFlake.initialize(0, 8);

        System.out.println(SnowFlake.getId());
    }
}