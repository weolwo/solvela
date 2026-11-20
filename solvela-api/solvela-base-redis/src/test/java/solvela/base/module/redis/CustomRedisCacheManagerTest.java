package solvela.base.module.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * {@code cacheName#ttl} 语法。网关那份（{@code AppCacheConfig.TtlInNameCacheManager}）写错即抛，
 * 这份以前是打一行 error 后退回默认的 7 天 —— 写错的 {@code @Cacheable} 会静默按 7 天走。
 */
class CustomRedisCacheManagerTest {

    private final CustomRedisCacheManager manager = new CustomRedisCacheManager(
            mock(RedisCacheWriter.class), RedisCacheConfiguration.defaultCacheConfig());

    @Test
    @DisplayName("🔴 TTL 段写错直接抛，报出缓存名与那一段")
    void 写错的TTL直接抛() {
        assertThatThrownBy(() -> manager.getCache("users#10mn"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("users#10mn")
                .hasMessageContaining("10mn");
    }

    @Test
    @DisplayName("合法的 TTL 照常生效 —— 确认上一条红的是写错，不是解析本身")
    void 合法TTL生效() {
        RedisCache cache = (RedisCache) manager.getCache("users#30m");

        assertThat(cache.getCacheConfiguration().getTtlFunction().getTimeToLive(null, null))
                .isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("不带 TTL 段按默认 7 天")
    void 不带TTL按默认() {
        RedisCache cache = (RedisCache) manager.getCache("users");

        assertThat(cache.getCacheConfiguration().getTtlFunction().getTimeToLive(null, null))
                .isEqualTo(Duration.ofDays(7));
    }
}
