package solvela.member.risk.config;

import solvela.security.IpRange;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import solvela.enums.SecurityListDimensionEnum;
import solvela.enums.SecurityListStatusEnum;
import solvela.enums.SecurityListTypeEnum;
import solvela.member.SecurityList;
import solvela.member.risk.MemberRiskProperties;
import solvela.member.risk.dao.SecurityListDao;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 安全名单的本地缓存。启动时加载一次，之后每 {@code cache-refresh}（默认 30 秒）重载一次 ——
 * 后台新增、撤销最多这么久在每个实例上生效，<b>不用重启</b>。
 *
 * <h3>到期在读的时候判</h3>
 * 不靠定时任务把过期的行改成撤销：缓存里留着过期条目也没关系，命中时比一下 {@code expire_time} 就行。
 * 后台列表里「已过期」同样是现算的。
 *
 * <h3>规模</h3>
 * 预期千条以内。IP 段按顺序逐条比，千条也只是微秒级；到万条再换成按起点排序后二分。
 *
 * @Date 2026-10-15
 */
@Slf4j
@Component
public class RiskListCache implements SmartInitializingSingleton {

    private final SecurityListDao listDao;

    private final PeriodicSnapshot<Snapshot> snapshot;

    /** 测试注入时钟用；生产就是当前时间 */
    private final Supplier<LocalDateTime> now;

    /**
     * 🔴 必须标 {@code @Autowired}：本类有<b>两个</b>构造器（另一个是给单测注入时钟的），
     * 不标的话 Spring 两个都不选、去找无参构造器，然后整个上下文起不来
     * （报的是「No default constructor found」，看不出跟第二个构造器有关）。
     */
    @Autowired
    public RiskListCache(SecurityListDao listDao, MemberRiskProperties properties) {
        this(listDao, properties, LocalDateTime::now);
    }

    RiskListCache(SecurityListDao listDao, MemberRiskProperties properties, Supplier<LocalDateTime> now) {
        this.listDao = listDao;
        this.now = now;
        this.snapshot = new PeriodicSnapshot<>("名单", this::load, properties.cacheRefresh());
    }

    /** 一条名单在缓存里的样子 */
    record Entry(long id, SecurityListTypeEnum type, int score, LocalDateTime expireTime) {
    }

    record RangeEntry(IpRange range, Entry entry) {
    }

    record Snapshot(Map<String, List<Entry>> exact, List<RangeEntry> ranges) {
    }

    @Override
    public void afterSingletonsInstantiated() {
        Snapshot loaded = snapshot.loadNow();
        log.info("【风控】名单加载完成：精确 {} 个键，IP 段 {} 条", loaded.exact().size(), loaded.ranges().size());
    }

    /** IP 命中（精确 IP 与 IP 段一起看）。ip 不是合法字面量时返回 {@link ListMatch#NONE} */
    /**
     * 立刻重载一次。后台改完名单调它。
     *
     * <p>⚠️ 只对<b>当前这个实例</b>生效 —— 多实例部署时其余实例按自己的周期刷
     * （默认 30 秒）。做成广播要引入一条消息通道，而「最多晚 30 秒生效」
     * 对人工加黑名单这件事完全够用；规则那边是同一个取舍。
     *
     * <p>失败只打日志：重载不成功不该让「名单已保存」变成一个报错 ——
     * 行已经落库了，30 秒后自然会被读到。
     */
    public void reload() {
        try {
            snapshot.loadNow();
        } catch (RuntimeException e) {
            log.warn("【风控】名单重载失败，等周期刷新", e);
        }
    }

    public ListMatch matchIp(String ip) {
        IpRange single = IpRange.single(ip);
        if (single == null) {
            return ListMatch.NONE;
        }
        Snapshot s = snapshot.get();
        LocalDateTime at = now.get();
        ListMatch.Builder b = new ListMatch.Builder();
        b.addAll(s.exact().get(key(SecurityListDimensionEnum.IP, canonicalIp(single))), at);
        for (RangeEntry r : s.ranges()) {
            if (r.range().contains(single)) {
                b.add(r.entry(), at);
            }
        }
        return b.build();
    }

    public ListMatch matchMember(Long memberId) {
        if (memberId == null) {
            return ListMatch.NONE;
        }
        return exact(SecurityListDimensionEnum.MEMBER, String.valueOf(memberId));
    }

    /** @param identityHashHex {@code PiiHasher} 输出，大小写不敏感 */
    public ListMatch matchIdentity(String identityHashHex) {
        if (identityHashHex == null || identityHashHex.isBlank()) {
            return ListMatch.NONE;
        }
        return exact(SecurityListDimensionEnum.IDENTITY, identityHashHex.trim().toLowerCase(Locale.ROOT));
    }

    private ListMatch exact(SecurityListDimensionEnum dimension, String value) {
        ListMatch.Builder b = new ListMatch.Builder();
        b.addAll(snapshot.get().exact().get(key(dimension, value)), now.get());
        return b.build();
    }

    private Snapshot load() {
        List<SecurityList> rows = listDao.selectList(new LambdaQueryWrapper<SecurityList>()
                .eq(SecurityList::getStatus, SecurityListStatusEnum.VALID));
        Map<String, List<Entry>> exact = new HashMap<>();
        List<RangeEntry> ranges = new ArrayList<>();
        for (SecurityList row : rows) {
            if (row.getListType() == null || row.getDimension() == null || row.getListValue() == null) {
                continue;
            }
            Entry entry = new Entry(row.getId(), row.getListType(),
                    row.getScore() == null ? 0 : row.getScore(), row.getExpireTime());
            try {
                switch (row.getDimension()) {
                    case IP -> exact.computeIfAbsent(
                            key(SecurityListDimensionEnum.IP, canonicalIp(IpRange.parse(row.getListValue()))),
                            k -> new ArrayList<>()).add(entry);
                    case IP_RANGE -> ranges.add(new RangeEntry(IpRange.parse(row.getListValue()), entry));
                    case IDENTITY -> exact.computeIfAbsent(key(SecurityListDimensionEnum.IDENTITY,
                            row.getListValue().trim().toLowerCase(Locale.ROOT)), k -> new ArrayList<>()).add(entry);
                    case MEMBER -> exact.computeIfAbsent(key(SecurityListDimensionEnum.MEMBER,
                            String.valueOf(Long.parseLong(row.getListValue().trim()))), k -> new ArrayList<>()).add(entry);
                }
            } catch (RuntimeException e) {
                // 后台保存时校验过，走到这里只可能是有人直接改了库。跳过这一条，不让整份名单加载失败
                log.error("【风控】名单第 {} 条的值读不出来，已跳过: {}", row.getId(), e.getMessage());
            }
        }
        return new Snapshot(exact, List.copyOf(ranges));
    }

    private static String key(SecurityListDimensionEnum dimension, String value) {
        return dimension.getValue() + ":" + value;
    }

    /** 同一个 IP 有多种写法（IPv6 尤甚），统一成数值再当键 */
    private static String canonicalIp(IpRange single) {
        return (single.v6() ? "6:" : "4:") + single.start().toString(16);
    }
}
