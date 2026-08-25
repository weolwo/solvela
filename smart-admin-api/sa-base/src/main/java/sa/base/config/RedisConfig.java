package sa.base.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import jakarta.annotation.Resource;
import sa.base.module.support.redis.CustomRedisCacheManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * redis配置
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2021-09-02 20:21:10
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
@Configuration
public class RedisConfig {

    private static final String REDIS_CACHE = "redis";

    public static final String REDIS_CACHE_PREFIX = "cache";

    @Resource
    private RedisConnectionFactory factory;

    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(factory);
        // 设置值（value）的序列化采用jackson2JsonRedisSerializer
        redisTemplate.setValueSerializer(jacksonJsonRedisSerializer());
        redisTemplate.setHashValueSerializer(jacksonJsonRedisSerializer());
        // 设置键（key）的序列化采用StringRedisSerializer。
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }
    /**
     * Jackson 3 的 ObjectMapper 是不可变的，所有 setXxx 都没了，配置一律在 builder 上做完再 build。
     * java.time 支持已内置，不再需要手动 registerModule(new JavaTimeModule())。
     * LaissezFaireSubTypeValidator 在 Jackson 3 里降级成了包级私有，换用 BasicPolymorphicTypeValidator。
     * ⚠️ 这里放行的是任意 Object 子类型，与原先 LaissezFaire 的宽松程度一致 ——
     * 存进 Redis 的值全部由本服务自己写入，不接受外部投递，所以维持原语义。
     */
    @Bean
    public JacksonJsonRedisSerializer<Object> jacksonJsonRedisSerializer() {
        ObjectMapper om = JsonMapper.builder()
                //告诉 Jackson：“别管字段是 private 还是 public，也别管有没有 getter/setter 方法，给我直接暴力反射，把对象里所有的属性全盘转化成 JSON！”
                .changeDefaultVisibility(vc -> vc.withVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY))
                // 默认把 LocalDateTime 变成时间戳的恶心设定关掉，让时间在 Redis 里以 2026-03-14 18:00:00 这种人类友好的字符串形式存在。
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                //如果 Java 对象里某个字段是 null，存进 Redis 的时候就干脆别写这个字段了。这在海量数据的 Redis 里能省下巨量的内存空间。
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .activateDefaultTyping(
                        BasicPolymorphicTypeValidator.builder().allowIfSubType(Object.class).build(),
                        DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY)
                //忽略无效字段
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
        return new JacksonJsonRedisSerializer<>(om, Object.class);
    }


    /**
     * 创建自定义Redis缓存管理器Bean 整合spring-cache
     * Redis连接工厂，用于建立与Redis服务器的连接
     *
     * <p>🔴 返回类型写<b>具体类型</b>而不是 {@code CacheManager}，是为了让类型匹配
     * 与 Bean 创建顺序无关。Spring 在 Bean <b>尚未实例化</b>时，只能从工厂方法的
     * <b>声明</b>返回类型推断它能满足哪些注入点；声明成宽泛的 {@code CacheManager}，
     * 按子类型（如 {@code RedisCacheManager}）找的注入点就会匹配不上。
     *
     * <p>这条不是纸上谈兵：2026-08-25 删演示模块时，原先「恰好」先一步触发
     * {@code cacheManager} 实例化的那个 {@code @Cacheable} Bean 没了，
     * 管理端当场启动失败 —— {@code required a bean of type 'RedisCacheManager'}，
     * 而报错完全指不到真正的原因（删几个跟缓存无关的业务类，为什么缓存就没了）。
     * 当时按此类型注入的 {@code RedisCacheServiceImpl} 后来也随 support/cache 一起删了，
     * 现在容器里已没有这样的注入点 —— <b>但具体返回类型请保留</b>：
     * 它的成本为零，而换回 {@code CacheManager} 等于把同一个坑重新埋回去。
     *
     * @return Redis 缓存管理器实例
     */
    @Bean
    @ConditionalOnProperty(prefix = "spring.cache", name = {"type"}, havingValue = REDIS_CACHE)
    public CustomRedisCacheManager cacheManager() {
        // 使用非阻塞模式的缓存写入器，适用于大多数高并发场景
        RedisCacheWriter redisCacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(factory);

        // 构建默认缓存配置
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                // 禁止缓存 null 值，避免缓存穿透
                .disableCachingNullValues()
                .computePrefixWith(name -> REDIS_CACHE_PREFIX + name + ":")
                // 使用 FastJSON 序列化缓存值，支持复杂对象
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jacksonJsonRedisSerializer()));

        // 返回自定义缓存管理器，支持 cacheName#ttl 格式与永久缓存（#-1）
        return new CustomRedisCacheManager(redisCacheWriter, defaultCacheConfig);
    }

    /*
     * 这里原先还有 redisCacheService() / caffeineCacheService() 两个 @Bean，
     * 随 support/cache 模块一起删除（2026-08-25）。那两个 Bean 只服务于后台的
     * 「手动清缓存」按钮，与 @Cacheable 的运行毫无关系 —— 缓存读写走的是上面的
     * cacheManager()，不经过它们。
     */

    @Bean
    public HashOperations<String, String, Object> hashOperations(RedisTemplate<String, Object> redisTemplate) {
        return redisTemplate.opsForHash();
    }

    @Bean
    public ValueOperations<String, String> valueOperations(RedisTemplate<String, String> redisTemplate) {
        return redisTemplate.opsForValue();
    }

    @Bean
    public ListOperations<String, Object> listOperations(RedisTemplate<String, Object> redisTemplate) {
        return redisTemplate.opsForList();
    }

    @Bean
    public SetOperations<String, Object> setOperations(RedisTemplate<String, Object> redisTemplate) {
        return redisTemplate.opsForSet();
    }

    @Bean
    public ZSetOperations<String, Object> zSetOperations(RedisTemplate<String, Object> redisTemplate) {
        return redisTemplate.opsForZSet();
    }

}
