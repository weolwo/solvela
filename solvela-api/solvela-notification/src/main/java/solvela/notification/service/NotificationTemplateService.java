package solvela.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.notification.NotificationTemplate;
import solvela.notification.dao.NotificationTemplateDao;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 模板查询 + 进程内缓存。
 *
 * <h3>两个缓存，寿命完全不同</h3>
 * <ul>
 *   <li>{@link #byVersionCache} —— key 是 {@code code:version}，<b>永久缓存</b>。
 *       因为模板按版本<b>不可变</b>：{@code (code, version)} 这一行一旦写进去就再也不会变，
 *       缓存永远不会脏。容量上界是「模板数 × 版本数」，几十到几百条，不需要淘汰。</li>
 *   <li>{@link #latestCache} —— key 是 {@code code}，<b>带 TTL</b>。
 *       「当前最新启用版本是哪个」会随运营改版而变，所以必须会过期。</li>
 * </ul>
 *
 * <p>🔴 这个区分是模板不可变设计白送的红利：<b>渲染历史通知（高频、读路径）走的是永久缓存，
 * 一次都不查库。</b> 如果模板是原地改的，这一层就只能全部带 TTL，
 * 收件箱详情页每次都可能回源。
 *
 * <h3>为什么不用 Redis / Spring Cache</h3>
 * 数据量是几十条、且各进程各缓存一份完全无害（模板版本不可变，不存在两个进程看到
 * 不同内容的问题）。上 Redis 就要给本模块加 {@code base-redis} 依赖，
 * 而它会跟着进 admin 与 app-biz 两个进程 —— 为几十条静态数据不值当。
 * TTL 内的短暂不一致最多是「运营刚改版、一分钟内还发着老版本」，可以接受。
 *
 * @Author alaric
 * @Date 2026-09-14
 * @Copyright weolwo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationTemplateService {

    /**
     * 「最新启用版本」的缓存存活时间。
     *
     * <p>60 秒是个折中：短到运营改完版不用等太久，长到高峰期不会每条通知都回源。
     * 真要立刻生效，重启或加一个手动刷新入口即可 —— 别把 TTL 调到 0，
     * 那等于每发一条通知查一次库。
     */
    private static final long LATEST_TTL_MILLIS = 60_000L;

    private final NotificationTemplateDao notificationTemplateDao;

    /** code:version -> 模板。不可变，永久缓存 */
    private final Map<String, NotificationTemplate> byVersionCache = new ConcurrentHashMap<>();

    /** code -> 最新启用版本 */
    private final Map<String, NotificationTemplate> latestCache = new ConcurrentHashMap<>();

    /** latestCache 的整体刷新时间戳。整体过期而不是逐条 —— 条数太少，不值得每条挂一个时间 */
    private final AtomicLong latestCacheAt = new AtomicLong(0L);

    /**
     * 取某个模板当前启用的最新版本，发送时用。
     *
     * @return 模板不存在或全部停用时返回 {@code null}
     */
    public NotificationTemplate getLatestEnabled(String templateCode) {
        long now = System.currentTimeMillis();
        if (now - latestCacheAt.get() > LATEST_TTL_MILLIS) {
            refreshLatest(now);
        }
        NotificationTemplate cached = latestCache.get(templateCode);
        if (cached != null) {
            return cached;
        }
        // 缓存里没有：可能是刚新增的模板、也可能真的不存在。回源一次，
        // 但【不回填 latestCache】—— 回填会让它绕过整体刷新的 TTL 语义
        NotificationTemplate fromDb = notificationTemplateDao.selectLatestEnabled(templateCode);
        if (fromDb != null) {
            cacheByVersion(fromDb);
        }
        return fromDb;
    }

    /**
     * 按 (code, version) 精确取一版，渲染历史通知用。
     *
     * <p>命中永久缓存时一次都不查库 —— 见类注释。
     *
     * @return 该版本已被物理删除时返回 {@code null}。理论上不该发生
     *         （模板只停用不删除），发生了说明有人违规删过表
     */
    public NotificationTemplate getByVersion(String templateCode, Integer version) {
        if (templateCode == null || version == null) {
            return null;
        }
        return byVersionCache.computeIfAbsent(
                cacheKey(templateCode, version),
                key -> notificationTemplateDao.selectByCodeAndVersion(templateCode, version));
    }

    private void refreshLatest(long now) {
        // 先占时间戳再查库：并发进来的其它线程看到时间戳已更新就不会重复查，
        // 代价是万一查库失败，这一个 TTL 周期内会继续用旧缓存 —— 可以接受
        if (!latestCacheAt.compareAndSet(latestCacheAt.get(), now)) {
            return;
        }
        try {
            var all = notificationTemplateDao.selectAllEnabled();
            latestCache.clear();
            for (NotificationTemplate template : all) {
                // selectAllEnabled 按 version 升序，后面的覆盖前面的，最终留下最大版本
                latestCache.put(template.getTemplateCode(), template);
                cacheByVersion(template);
            }
            log.debug("【通知模板】缓存刷新完成，启用中模板 {} 个", latestCache.size());
        } catch (Exception e) {
            // 刷新失败不能让发通知这条路径挂掉：旧缓存继续用，下个周期再试
            log.error("【通知模板】缓存刷新失败，继续使用旧缓存", e);
        }
    }

    private void cacheByVersion(NotificationTemplate template) {
        byVersionCache.putIfAbsent(cacheKey(template.getTemplateCode(), template.getVersion()), template);
    }

    private static String cacheKey(String templateCode, Integer version) {
        return templateCode + ":" + version;
    }
}
