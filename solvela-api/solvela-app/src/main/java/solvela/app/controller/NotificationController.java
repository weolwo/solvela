package solvela.app.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import solvela.app.auth.CurrentMember;
import solvela.app.web.ApiErrors;
import solvela.app.web.ClientIp;
import solvela.app.web.ApiException;
import solvela.member.api.AnnouncementView;
import solvela.member.api.NotificationApi;
import solvela.member.api.NotificationDetailView;
import solvela.member.api.NotificationPageCmd;
import solvela.member.api.NotificationPageView;
import solvela.member.api.NotificationPreferenceView;

import java.util.List;

/**
 * 消息中心。
 *
 * <h3>全部要登录，没有 @Anonymous</h3>
 * 站内信是<b>写给某个人</b>的东西，匿名访问没有任何意义。
 *
 * <h3>🔴 会员号一律从登录态取</h3>
 * 本类的每个方法都不收 memberId —— 收了的话，改一下 query string
 * 就能翻别人的收件箱。下游那些 {@code @RequestParam Long memberId}
 * 是<b>服务间调用</b>的参数，公网这一层不暴露。
 *
 * <h3>两个 tab，两条路径</h3>
 * {@code /notification/**} 是通知，{@code /notification/announcement/**} 是公告。
 * 各查各的、各自分页，<b>没有归并</b>。
 *
 * <p>入口总红点 = {@link #unreadCount()} —— 服务端把两个数加好再给端上，
 * 免得三个客户端各加一遍、各错一次。
 *
 * @Author alaric
 * @Date 2026-09-14
 */
@Tag(name = "消息中心")
@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationApi notificationApi;

    // ------------------------------------------------------------------ 通知 tab

    /**
     * 通知列表。顺带返回通知 tab 的未读数。
     *
     * @param category 分类筛选：SYSTEM / TRADE / MARKETING，不传 = 全部
     */
    @GetMapping
    public NotificationPageView list(@RequestParam(required = false) String category,
                                     @RequestParam(required = false) Boolean unreadOnly,
                                     @RequestParam(defaultValue = "1") Integer pageNum,
                                     @RequestParam(defaultValue = "20") Integer pageSize) {
        return notificationApi.page(new NotificationPageCmd(
                CurrentMember.require().memberId(), category, unreadOnly, pageNum, pageSize));
    }

    /**
     * 通知详情。<b>看了就算读了</b> —— 打开详情即标记已读，不用端上再调一次。
     *
     * <p>「不存在」与「不是你的」给同一个 404：通知 id 是自增的、可枚举，
     * 区分开来等于确认某个 id 存在，而这条信息对攻击者有用、对用户无用。
     */
    @GetMapping("/{id}")
    public NotificationDetailView detail(@PathVariable Long id) {
        Long memberId = CurrentMember.require().memberId();
        NotificationDetailView view = notificationApi.detail(id, memberId);
        if (view == null) {
            throw new ApiException(ApiErrors.NOT_FOUND, "消息不存在");
        }
        // 已读标记放在详情里做，而不是让端上单独调一次：
        // 端上那一次调用一定有人忘了写，而忘了的表现是「点开了红点还在」
        notificationApi.markRead(id, memberId);
        return view;
    }

    /** 通知全部已读。一次 UPDATE，返回本次标记条数 */
    @PostMapping("/read-all")
    public int markAllRead() {
        return notificationApi.markAllRead(CurrentMember.require().memberId());
    }

    // ------------------------------------------------------------------ 公告 tab

    /**
     * 公告列表。
     *
     * @param lastId 上一页最后一条的 id，首页不传。用游标翻页而不是页码 ——
     *               公告会持续新增，页码分页在第 2 页会重复看到被挤下来的那一条
     */
    @GetMapping("/announcement")
    public List<AnnouncementView> announcements(@RequestParam(required = false) Long lastId,
                                                @RequestParam(required = false) Integer limit) {
        return notificationApi.announcements(CurrentMember.require().memberId(), lastId, limit);
    }

    /**
     * 读了某条公告。
     *
     * <p>⚠️ <b>不支持跳读</b>：点开任何一条，它<b>之前</b>的全部算已读。
     * 前端把公告和通知分成两个 tab 正是这条语义成立的前提 ——
     * 两种已读行为各待在自己的列表里，用户不会在同一个列表里看到两种规则。
     */
    @PostMapping("/announcement/{id}/read")
    public void readAnnouncement(@PathVariable Long id) {
        notificationApi.readAnnouncement(id, CurrentMember.require().memberId());
    }

    /** 公告全部已读 */
    @PostMapping("/announcement/read-all")
    public void readAllAnnouncements() {
        notificationApi.readAllAnnouncements(CurrentMember.require().memberId());
    }

    /**
     * 进 App 时查：有没有必须先确认才能继续用的公告。
     *
     * <p>返回非空就弹窗，用户点了「我已阅读」再调 {@link #ackAnnouncement}。
     * <b>没确认就一直返回</b> —— 这个机制自带催办。
     */
    @GetMapping("/announcement/pending-ack")
    public List<AnnouncementView> pendingAck() {
        return notificationApi.pendingAck(CurrentMember.require().memberId());
    }

    /**
     * 用户点了「我已阅读并知悉」。
     *
     * <p>IP 由<b>服务端从请求里取</b>，不收客户端传的 —— 合规留痕的字段
     * 让被记录方自己填，那条记录就一文不值了。
     */
    @PostMapping("/announcement/{id}/ack")
    public void ackAnnouncement(@PathVariable Long id, HttpServletRequest request) {
        notificationApi.ackAnnouncement(id, CurrentMember.require().memberId(), ClientIp.of(request));
    }

    // ------------------------------------------------------------------ 红点与设置

    /**
     * 入口总红点 = 通知未读 + 公告未读。
     *
     * <p>两个数<b>在服务端加好</b>再下发：让三个客户端各加一遍，
     * 迟早有一个漏掉公告那一半，而那种 bug 只会表现成「红点数偏小」，
     * 没人会去对账。
     */
    @GetMapping("/unread-count")
    public long unreadCount() {
        Long memberId = CurrentMember.require().memberId();
        NotificationPageView page = notificationApi.page(
                new NotificationPageCmd(memberId, null, null, 1, 1));
        return page.unreadCount() + notificationApi.announcementUnread(memberId);
    }

    /** 免打扰设置。注意<b>没有</b>系统通知开关 —— 那一类关不掉 */
    @GetMapping("/preference")
    public NotificationPreferenceView preference() {
        return notificationApi.preference(CurrentMember.require().memberId());
    }

    @PostMapping("/preference")
    public void savePreference(@RequestParam boolean tradeEnabled,
                               @RequestParam boolean marketingEnabled) {
        notificationApi.savePreference(CurrentMember.require().memberId(), tradeEnabled, marketingEnabled);
    }
}
