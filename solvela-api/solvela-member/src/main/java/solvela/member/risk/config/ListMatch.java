package solvela.member.risk.config;

import solvela.enums.MemberRiskListTypeEnum;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 一个值在名单里的命中情况（已排除过期条目）。
 *
 * <p>同时在黑、白名单里时<b>黑名单优先</b> —— 这个判断留给调用方，本类如实报告三种都有没有。
 *
 * @param blackEntryId 命中的黑名单条目，未命中为 null（进命中明细，客服能顺着它找到是谁加的）
 * @param greyScore    命中的灰名单条目里最高的分；未命中为 0
 */
public record ListMatch(Long blackEntryId, boolean white, int greyScore) {

    public static final ListMatch NONE = new ListMatch(null, false, 0);

    public boolean black() {
        return blackEntryId != null;
    }

    public boolean grey() {
        return greyScore > 0;
    }

    static final class Builder {

        private Long blackEntryId;

        private boolean white;

        private int greyScore;

        void addAll(List<RiskListCache.Entry> entries, LocalDateTime now) {
            if (entries != null) {
                entries.forEach(e -> add(e, now));
            }
        }

        void add(RiskListCache.Entry e, LocalDateTime now) {
            if (e.expireTime() != null && !e.expireTime().isAfter(now)) {
                return;
            }
            if (e.type() == MemberRiskListTypeEnum.BLACK) {
                blackEntryId = blackEntryId == null ? e.id() : Math.min(blackEntryId, e.id());
            } else if (e.type() == MemberRiskListTypeEnum.WHITE) {
                white = true;
            } else if (e.type() == MemberRiskListTypeEnum.GREY) {
                greyScore = Math.max(greyScore, e.score());
            }
        }

        ListMatch build() {
            return blackEntryId == null && !white && greyScore == 0
                    ? NONE : new ListMatch(blackEntryId, white, greyScore);
        }
    }
}
