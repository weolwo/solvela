package solvela.app.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.time.Duration;

/**
 * 网关的缓存。<b>只有一个缓存：认证热路径的会员身份</b>（{@code MemberPrincipalLoader}）。
 *
 * <h3>为什么网关自己写，而不是用 base-redis 的 RedisConfig</h3>
 * base-redis 那个 {@code @Configuration} 里有 RedisTemplate、缓存管理器、Redisson
 * 这一批 bean，网关<b>一个都用不到</b> —— 它只要 {@code StringRedisTemplate}
 * （Spring Boot 自动配置就给）加这里的缓存管理器。
 *
 * <p>而依赖 base-redis 的代价是 27 个传递 jar：<b>Redisson 加 9 个 netty</b>、
 * caffeine、aspectjweaver。一个转发 HTTP、解析令牌的进程没有理由背着它们。
 *
 * <h3>{@code cacheName#ttl} 这个语法不能丢</h3>
 * {@code MemberPrincipalLoader} 写的是 {@code @Cacheable("app_member_principal#30m")}，
 * TTL 编在缓存名里。这是 base-redis 的 {@code CustomRedisCacheManager} 定的规矩，
 * 网关这边必须照样支持，否则那个注解会静默按默认 TTL 走 —— 身份缓存永不过期，
 * 后台改了会员资料，用户可能一直看到旧的。
 *
 * <h3>序列化配置与 base 保持一致是有原因的</h3>
 * 开着 default typing（值里带类型信息）。<b>不是为了扩展性，是为了兼容既有缓存条目</b>：
 * 换一套序列化方式，发版那一刻起所有在飞的缓存值都反序列化失败。
 * 缓存键前缀是 {@code cache:app_member_principal:}，不与任何其它进程共享。
 *
 * <h3>🔴 就算「保持一致」也要装一个安全网</h3>
 * 上一条只能保证<b>这一次</b>发版不炸缓存，挡不住下一次——序列化方式、
 * {@code MemberPrincipal} 的字段、Jackson 版本，任何一个变了，Redis 里在飞的旧记录
 * 都会读不出来。默认情况下 Spring Cache 把这种读取异常原样抛出，表现是
 * 「一条读不出来的缓存条目 = 这个会员接下来 30 分钟内每一个请求都 500」——
 * 因为 {@code MemberPrincipalLoader.load} 在认证过滤器里，每个带令牌的请求都会先读它。
 * 见 {@link AppCacheErrorHandler} 与下面的 {@link #cachingConfigurer}：缓存读写失败一律
 * 降级为回源，不让缓存的问题变成用户面的问题。
 */
@Configuration
public class AppCacheConfig {

    /** 与 base-redis 的 CustomRedisCacheManager 一致，换了会读不到既有缓存 */
    private static final String CACHE_PREFIX = "cache:";

    private static final String TTL_SEPARATOR = "#";

    private static final Duration DEFAULT_TTL = Duration.ofDays(7);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                // 禁止缓存 null：认证链路上「查不到这个会员」是常态（令牌过期、会员注销），
                // 缓存它等于让一次误判持续 30 分钟
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(valueSerializer()));

        return new TtlInNameCacheManager(RedisCacheWriter.nonLockingRedisCacheWriter(factory), defaults);
    }

    /**
     * 把 {@link AppCacheErrorHandler} 接进 {@code @Cacheable} 的执行链路。
     *
     * <h3>为什么不直接声明一个 {@code @Bean CacheErrorHandler}</h3>
     * 试过——不生效。Spring 的缓存切面只在存在 {@link CachingConfigurer} 时
     * 才会去问它要 errorHandler，单独扔一个 {@code CacheErrorHandler} 类型的 bean
     * 到容器里不会被自动捡起来，表现是「加了处理器但异常照样往外抛，跟没加一样」。
     *
     * <h3>为什么 {@code cacheManager()} 要显式返回注入进来的实例，而不是留空</h3>
     * {@code CachingConfigurer.cacheManager()} 默认返回 null。如果真返回 null，
     * Spring 会把切面的 cacheManager 来源<b>整个切换成这个配置器</b>，
     * 而不是「配置器没提供就退回原来那个 {@code @Bean}」——最终表现是
     * <b>启动直接找不到 cacheManager 报错</b>，而不是优雅降级。
     * 所以这里必须显式把上面那个 {@code cacheManager} bean 传回去，
     * 只让 errorHandler 这一项生效。
     */
    @Bean
    public CachingConfigurer cachingConfigurer(RedisCacheManager cacheManager,
                                               AppCacheErrorHandler errorHandler) {
        return new CachingConfigurer() {
            @Override
            public CacheManager cacheManager() {
                return cacheManager;
            }

            @Override
            public CacheErrorHandler errorHandler() {
                return errorHandler;
            }
        };
    }

    private static JacksonJsonRedisSerializer<Object> valueSerializer() {
        ObjectMapper om = JsonMapper.builder()
                // 不管字段可见性、不管有没有 getter，直接反射 —— MemberPrincipal 是 record
                .changeDefaultVisibility(vc -> vc.withVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY))
                // 时间存成人能读的字符串，不是时间戳 —— 排查缓存内容时省事
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                /*
                 * 🔴 必须是 NON_FINAL_AND_RECORDS，不能是 NON_FINAL —— 这不是风格选择，
                 * 是一个真实存在过的 bug：Java record 隐式 final，NON_FINAL 这个策略名字的意思
                 * 正是「只给非 final 的运行时类型写 @class」，于是【每一个】缓存进这里的 record
                 * （本模块只缓存 MemberPrincipal 一种东西，恰好就是 record）永远不会带上 @class。
                 *
                 * 写的时候没有任何报错——写成功了，只是没类型信息。真正暴露是在读的时候：
                 * 反序列化目标类型是 Object.class（见下面 new JacksonJsonRedisSerializer<>(om,
                 * Object.class)），没有 @class 就无法确定该转成哪个类，直接抛
                 * InvalidTypeIdException: missing type id property '@class'。
                 *
                 * 表现就是：登录成功 → 首次写缓存（NON_FINAL 静默不写类型）→ 30 分钟 TTL 内
                 * 这个会员的【每一个】认证请求都要经 MemberPrincipalLoader.load 读这份缓存 →
                 * 每次都 500，包括退出登录。不是「旧数据」，是这份配置从第一次写入起就必然复现，
                 * 对任何账号、任何时候登录都一样——只是复现窗口跟着 TTL 走，看起来像间歇性的。
                 *
                 * NON_FINAL_AND_RECORDS 是 Jackson 3.1 专门为这种情况加的枚举值：
                 * 语义与 NON_FINAL 完全一致，只是额外覆盖 record。
                 */
                .activateDefaultTyping(
                        BasicPolymorphicTypeValidator.builder().allowIfSubType(Object.class).build(),
                        DefaultTyping.NON_FINAL_AND_RECORDS, JsonTypeInfo.As.PROPERTY)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
        return new JacksonJsonRedisSerializer<>(om, Object.class);
    }

    /**
     * 支持 {@code cacheName#ttl} 的缓存管理器，{@code #-1} 表示永不过期。
     *
     * <p>把 TTL 编在缓存名里而不是配置文件里，是为了让「这个缓存活多久」
     * 出现在<b>用它的那一行代码旁边</b> —— 配置文件里的一张 TTL 表，
     * 改代码的人不会去看。
     */
    private static final class TtlInNameCacheManager extends RedisCacheManager {

        private TtlInNameCacheManager(RedisCacheWriter writer, RedisCacheConfiguration defaults) {
            super(writer, defaults);
        }

        @Override
        protected RedisCache createRedisCache(String name, RedisCacheConfiguration cacheConfig) {
            Duration ttl = parseTtl(name);
            RedisCacheConfiguration config = cacheConfig
                    // 前缀里去掉 #ttl 那一段，否则 Redis 里的键会长成 cache:xxx#30m:1001
                    .computePrefixWith(cacheName -> CACHE_PREFIX + stripTtl(cacheName) + ":")
                    .entryTtl(ttl == null ? DEFAULT_TTL : ttl);
            return super.createRedisCache(name, config);
        }

        private static String stripTtl(String name) {
            return name == null ? "" : name.split(TTL_SEPARATOR, 2)[0].trim();
        }

        private static Duration parseTtl(String name) {
            if (name == null) {
                return null;
            }
            String[] parts = name.split(TTL_SEPARATOR, 2);
            if (parts.length < 2 || parts[1].isBlank()) {
                return null;
            }
            String raw = parts[1].trim();
            if ("-1".equals(raw)) {
                // Spring Data Redis 里负数 Duration 表示永不过期
                return Duration.ofMillis(-1);
            }
            try {
                Duration ttl = DurationStyle.detectAndParse(raw);
                return ttl.getSeconds() > 0 ? ttl : null;
            } catch (IllegalArgumentException e) {
                // 🔴 不能吞成默认 TTL 就算了：写错的 @Cacheable 会静默按 7 天走，
                // 而「缓存怎么一直不过期」是最难查的一类问题
                throw new IllegalStateException(
                        "缓存名 '" + name + "' 的 TTL 段 '" + raw + "' 解析失败，"
                                + "格式见 DurationStyle（如 30m / 2h / 7d / -1 表示永久）", e);
            }
        }
    }
}
