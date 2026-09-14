package solvela.member.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * 消息中心的对外契约。实现在 {@code solvela-notification}，薄壳在 app-biz。
 *
 * <h3>为什么在 member-api</h3>
 * 本模块的 pom 写着「粒度对齐<b>服务</b>、不对齐今天的 maven 模块」。
 * 通知是<b>挂在会员身上</b>的东西（{@code t_member_notification} /
 * {@code t_member_announcement_cursor} 都是一人一行或按会员查），
 * 将来资产域独立出去时它会跟着会员走，所以契约放这里。
 *
 * <h3>🔴 通知与公告是两条独立的路径，没有归并</h3>
 * 前端是<b>两个 tab</b>：通知一个、公告一个，各查各的、各自分页。
 *
 * <p>早期方案写的是「两路内存归并成一个收件箱列表」，还特别强调不要用 SQL UNION
 * 分页（两边过滤条件完全不同，UNION 之后排序分页会让两边的索引都用不上）。
 * 分 tab 之后这段复杂度整个消失了。
 *
 * <p>入口总红点 = {@link #page} 的 {@code unreadCount} + {@link #announcementUnread}，
 * 两个数在端上相加。
 *
 * <h3>路径前缀 /internal 是有意的</h3>
 * 公网入口是网关自己的 {@code /notification/**}，鉴权、字段裁剪、措辞都在那一层。
 * <b>会员号一律由网关从登录态取</b>，这些方法收 memberId 是因为它们在服务间调用，
 * 不是让客户端传。
 */
@HttpExchange("/internal/notification")
public interface NotificationApi {

    // ------------------------------------------------------------------ 通知 tab

    /** 收件箱分页。顺带把未读数一起返回，省端上一次往返 */
    @PostExchange("/page")
    NotificationPageView page(@RequestBody NotificationPageCmd cmd);

    /**
     * 通知详情。这里才渲染正文。
     *
     * @return 不存在或不属于这个会员时返回 {@code null} —— 两种情况<b>给同一个结果</b>，
     *         区分开来等于确认某个 id 存在，而那条信息对攻击者有用、对用户无用
     */
    @GetExchange("/detail/{id}")
    NotificationDetailView detail(@PathVariable Long id, @RequestParam Long memberId);

    /** 标记一条已读。返回是否真的改了 */
    @PostExchange("/read/{id}")
    boolean markRead(@PathVariable Long id, @RequestParam Long memberId);

    /** 通知全部已读。一次 UPDATE，返回本次标记的条数 */
    @PostExchange("/read-all")
    int markAllRead(@RequestParam Long memberId);

    // ------------------------------------------------------------------ 公告 tab

    /**
     * 公告列表。
     *
     * @param lastId 上一页最后一条的 id，首页传 null。用游标翻页而不是 offset ——
     *               公告会持续新增，offset 分页在第 2 页会重复看到被挤下来的那一条
     */
    @GetExchange("/announcement")
    List<AnnouncementView> announcements(@RequestParam Long memberId,
                                         @RequestParam(required = false) Long lastId,
                                         @RequestParam(required = false) Integer limit);

    /** 公告 tab 的未读数 */
    @GetExchange("/announcement/unread")
    long announcementUnread(@RequestParam Long memberId);

    /** 读了某条公告：把游标推到它（它之前的全部算已读） */
    @PostExchange("/announcement/read/{id}")
    void readAnnouncement(@PathVariable Long id, @RequestParam Long memberId);

    /** 公告全部已读：游标推到当前可见公告的最大 id */
    @PostExchange("/announcement/read-all")
    void readAllAnnouncements(@RequestParam Long memberId);

    /**
     * 进 App 时查：有没有必须先确认才能继续用的公告。
     *
     * <p>非空就弹窗。没确认就一直返回 —— <b>这个机制自带催办</b>，
     * 所以服务端不需要「谁还没确认」那种反连接查询。
     */
    @GetExchange("/announcement/pending-ack")
    List<AnnouncementView> pendingAck(@RequestParam Long memberId);

    /** 用户点了「我已阅读并知悉」。返回是否是第一次确认 */
    @PostExchange("/announcement/ack/{id}")
    boolean ackAnnouncement(@PathVariable Long id, @RequestParam Long memberId,
                            @RequestParam(required = false) String ackIp);

    // ------------------------------------------------------------------ 免打扰

    @GetExchange("/preference")
    NotificationPreferenceView preference(@RequestParam Long memberId);

    @PostExchange("/preference")
    void savePreference(@RequestParam Long memberId,
                        @RequestParam boolean tradeEnabled,
                        @RequestParam boolean marketingEnabled);
}
