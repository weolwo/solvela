package solvela.app.constant;

import solvela.base.constant.CacheKeyConst;

/**
 * solvela-app 的缓存 key。
 *
 * <p>继承 {@link CacheKeyConst} 与管理端的 {@code AdminCacheConst} 同构 ——
 * solvela-base 里的通用缓存名（字典等）两个进程共用同一份常量，避免同一份数据
 * 在两边用了不同的 cacheName 而各缓存一份、失效时只清掉一边。
 *
 * <p>🔴 本类里新增的 key <b>必须与 AdminCacheConst 的取值不重名</b>。
 * 两个进程连同一个 Redis，cacheName 就是 key 前缀（{@code cache{name}:{key}}）——
 * 重名意味着两套结构不同的对象写进同一批 key，反序列化时才炸。
 *
 * @Date 2026-08-25
 */
public class AppCacheConst extends CacheKeyConst {

    public static class Login {

        /**
         * 当前登录会员信息，key 为 memberId。
         *
         * <p>{@code #30m} 是 {@code CustomRedisCacheManager} 的 TTL 语法（30 分钟）。
         * 为什么要有这层缓存：拦截器<b>每个请求</b>都要还原登录态，不缓存就是每个请求一次
         * {@code SELECT ... FROM t_member} —— C 端的 QPS 特征下这是最先被打爆的那条 SQL。
         *
         * <p>为什么不设成永久：会员的昵称/头像/状态改了之后，缓存是唯一的陈旧来源。
         * 改资料的接口会 {@code @CacheEvict} 主动清（见 {@code MemberLoginManager}），
         * 但「运营在后台把人冻结了」这条路径走的是<b>管理端进程</b>，它清不到也不该清
         * 本模块的缓存。30 分钟是那种情况下的最长容忍窗口 —— 要更快就得上广播失效，
         * 那是另一个量级的复杂度，当前规模不值得。
         */
        public static final String REQUEST_MEMBER = "request_member#30m";
    }
}
