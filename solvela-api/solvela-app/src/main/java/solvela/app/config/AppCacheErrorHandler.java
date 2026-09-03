package solvela.app.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.stereotype.Component;

/**
 * 缓存出错时<b>降级成回源</b>，不让一条坏缓存拖垮整条认证链路。
 *
 * <h3>这修的是什么</h3>
 * {@code MemberPrincipalLoader.load} 是认证热路径：<b>每一个带令牌的请求</b>都会先读一次
 * {@code app_member_principal} 缓存。Spring Cache 的默认行为（{@code SimpleCacheErrorHandler}）
 * 是把缓存读写异常原样抛出 —— 一条读不出来的缓存条目，表现就是这一个会员的<b>每一个请求</b>
 * 都 500，直到那条 30 分钟 TTL 的记录自然过期。用户报的现象通常是「点什么都报错」，
 * 而根因只是一次缓存反序列化失败。
 *
 * <p>缓存条目为什么会读不出来，最常见的一种：{@code MemberPrincipal} 或
 * {@code AppCacheConfig} 的序列化配置发生过变化，Redis 里还留着<b>上一版格式</b>写的旧记录。
 * {@code MemberPrincipal.of} 的类注释里早就点过这件事——"缓存里的旧数据反序列化失败是在
 * 下一次发布之后才暴露的"——但那次只做到了「会员域加字段不牵连网关缓存」，没有堵上
 * 「网关自己的缓存格式变了怎么办」这一半。本类补上后一半。
 *
 * <h3>为什么是「降级」而不是「先修好格式」</h3>
 * 格式不兼容<b>只要发生过一次跨版本发布就会再发生</b>——序列化方式改了、字段改了、
 * 甚至只是升级了 Jackson。缓存的本质是「加速手段，不是权威数据源」：
 * 权威数据（{@link solvela.member.api.MemberAuthApi#getAuthIdentity}）永远在，
 * 读缓存失败时退回去查一次是正确行为，而不是把「缓存脏了」升级成「用户功能全挂」。
 *
 * <p>四类操作都降级：GET 失败当作未命中（触发回源，并且 Spring Cache 会在方法返回后
 * 尝试把新结果 PUT 回缓存，等于顺手把这条坏记录<b>自我修复</b>掉）；
 * PUT/EVICT/CLEAR 失败只记警告——它们本来就是「让下次更快」的优化动作，
 * 失败不该影响本次请求已经算出来的结果。
 *
 * <h3>为什么日志级别是 warn 不是 error</h3>
 * 这里捕获的异常<b>已经被这个类兜住、不会向上冒</b>，请求最终会成功。
 * 用 error 会让每一次自愈都在监控上炸出一条「服务出错了」，而实际用户体验完全正常。
 * 但也不能不打日志——如果同一个 key 反复出现在这里，说明缓存<b>持续</b>写不进去
 * （比如序列化配置本身就是错的，每次 PUT 完下次 GET 还是失败），那是需要运维介入的信号。
 */
@Slf4j
@Component
public class AppCacheErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        log.warn("[缓存] 读取失败，降级为回源: cache={}, key={}, 原因={}",
                cache.getName(), key, exception.toString());
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        log.warn("[缓存] 写入失败，本次请求结果不受影响: cache={}, key={}, 原因={}",
                cache.getName(), key, exception.toString());
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.warn("[缓存] 失效指令失败，条目将按 TTL 自然过期: cache={}, key={}, 原因={}",
                cache.getName(), key, exception.toString());
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.warn("[缓存] 清空失败: cache={}, 原因={}", cache.getName(), exception.toString());
    }
}
