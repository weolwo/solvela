package solvela.app.ratelimit;

import lombok.extern.slf4j.Slf4j;
import solvela.security.GatewayRuleSnapshot;
import solvela.security.IpRange;

import java.util.ArrayList;
import java.util.List;

/**
 * 快照里的 IP / IP 段名单，按黑、灰、白分好，供限流（灰、白）与安全闸（黑、白）共用。
 *
 * <p>只在快照换了的时候建一次（调用方按快照身份缓存）。到期时间按网关自己的时钟判，不等下一次快照。
 * 规模预期千条以内，逐条比 CIDR 是微秒级。
 *
 * @Date 2026-10-17
 */
@Slf4j
final class IpLists {

    static final int BLACK = 1, GREY = 2, WHITE = 3;

    /** 名单条目来源：1-人工, 2-自动 */
    static final int SOURCE_MANUAL = 1;

    /** 一条黑名单命中。{@code value} 是名单里那一行的原文（被挡次数记在它头上） */
    record BlackHit(boolean manual, Long expireAtMillis, String value) {
    }

    private record Entry(IpRange range, String value, int source, Long expireAtMillis) {

        boolean live(long nowMillis) {
            return expireAtMillis == null || expireAtMillis > nowMillis;
        }
    }

    private final List<Entry> black = new ArrayList<>();

    private final List<Entry> grey = new ArrayList<>();

    private final List<Entry> white = new ArrayList<>();

    IpLists(GatewayRuleSnapshot snapshot) {
        if (snapshot.ipLists() == null) {
            return;
        }
        for (GatewayRuleSnapshot.IpEntry e : snapshot.ipLists()) {
            List<Entry> bucket = switch (e.listType()) {
                case BLACK -> black;
                case GREY -> grey;
                case WHITE -> white;
                default -> null;
            };
            if (bucket == null) {
                continue;
            }
            try {
                bucket.add(new Entry(IpRange.parse(e.value()), e.value(), e.source(), e.expireAt()));
            } catch (IllegalArgumentException ex) {
                log.warn("【安全名单】快照里有解不开的 IP，忽略: {}", e.value());
            }
        }
    }

    boolean white(IpRange ip, long nowMillis) {
        return ip != null && find(white, ip, nowMillis) != null;
    }

    boolean grey(IpRange ip, long nowMillis) {
        return ip != null && find(grey, ip, nowMillis) != null;
    }

    /**
     * 黑名单命中。同时命中人工与自动条目时报人工的（它不豁免老设备）；同为一类时报到期最晚的。
     *
     * @return 没命中为 null
     */
    BlackHit black(IpRange ip, long nowMillis) {
        if (ip == null) {
            return null;
        }
        BlackHit best = null;
        for (Entry e : black) {
            if (!e.live(nowMillis) || !e.range().contains(ip)) {
                continue;
            }
            BlackHit hit = new BlackHit(e.source() == SOURCE_MANUAL, e.expireAtMillis(), e.value());
            if (best == null || better(hit, best)) {
                best = hit;
            }
        }
        return best;
    }

    private static boolean better(BlackHit a, BlackHit b) {
        if (a.manual() != b.manual()) {
            return a.manual();
        }
        if (b.expireAtMillis() == null) {
            return false;
        }
        return a.expireAtMillis() == null || a.expireAtMillis() > b.expireAtMillis();
    }

    private static Entry find(List<Entry> entries, IpRange ip, long nowMillis) {
        for (Entry e : entries) {
            if (e.live(nowMillis) && e.range().contains(ip)) {
                return e;
            }
        }
        return null;
    }
}
