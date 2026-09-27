package solvela.app.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import solvela.member.api.NotificationApi;
import solvela.member.api.NotificationPageCmd;
import solvela.member.api.NotificationPageView;

/**
 * 消息入口的总红点 = 通知未读 + 公告未读。
 *
 * <p>两个数<b>在服务端加好</b>再下发：让客户端各加一遍，迟早有一个漏掉公告那一半，
 * 而那种 bug 只会表现成「红点数偏小」，没人会去对账。
 *
 * <p>单独成一个组件，是因为有两处要用（消息中心的 unread-count、「我的」页汇总）——
 * 两处各写一遍「page 取 unreadCount + announcementUnread」，就又回到了上面那个风险。
 */
@Component
@RequiredArgsConstructor
public class UnreadCounter {

    private final NotificationApi notificationApi;

    public long count(Long memberId) {
        NotificationPageView page = notificationApi.page(new NotificationPageCmd(memberId, null, null, 1, 1));
        return page.unreadCount() + notificationApi.announcementUnread(memberId);
    }
}
