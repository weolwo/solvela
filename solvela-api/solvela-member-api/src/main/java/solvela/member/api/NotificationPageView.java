package solvela.member.api;

import java.util.List;

/**
 * 收件箱分页结果。
 *
 * <p>带上 {@code unreadCount} 是刻意的：端上拉列表的同时就把红点数拿到了，
 * 省一次往返。两个数在同一次查询里取，也不会出现「列表显示 3 条未读、
 * 红点写着 5」这种自相矛盾。
 */
public record NotificationPageView(
        List<NotificationView> list,
        long total,
        long unreadCount) {
}
