package solvela.biz.server.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import solvela.member.api.AnnouncementView;
import solvela.member.api.NotificationApi;
import solvela.member.api.NotificationDetailView;
import solvela.member.api.NotificationPageCmd;
import solvela.member.api.NotificationPageView;
import solvela.member.api.NotificationPreferenceView;
import solvela.member.api.NotificationView;
import solvela.member.service.MemberService;
import solvela.notification.Announcement;
import solvela.notification.MemberNotificationPreference;
import solvela.enums.NotificationCategoryEnum;
import solvela.notification.domain.dto.MemberNotificationDTO;
import solvela.notification.domain.dto.MemberNotificationDetailDTO;
import solvela.notification.domain.query.MemberNotificationQuery;
import solvela.notification.service.AnnouncementService;
import solvela.notification.service.NotificationInboxService;
import solvela.notification.service.NotificationPreferenceService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * {@link NotificationApi} 的 HTTP 薄壳。
 *
 * <h3>为什么组装逻辑在这里而不是 solvela-notification 里</h3>
 * 公告的人群规则要用会员的<b>注册时间</b>，而 {@code solvela-notification}
 * <b>不认识会员域</b>（它排在全部业务域之前，反向依赖会直接成环）。
 * 这里是两个域唯一能碰面的地方。
 *
 * <p>所以本类比 {@code MallInternalController} 那种纯转发要"厚"一点 ——
 * 厚出来的那部分全是「把 member 的注册时间喂给 notification」，没有别的。
 *
 * <h3>⚠️ 拿不到注册时间时只放行 ALL 人群</h3>
 * {@code memberCreateTime} 为 null 时，Mapper 里那段人群条件只会匹配
 * {@code audience_type = 'ALL'}。宁可少发一条定向公告，也不要把一条
 * 本该定向的公告发给全体。
 *
 * @Author alaric
 * @Date 2026-09-14
 */
@RestController
@RequiredArgsConstructor
public class NotificationInternalController implements NotificationApi {

    /** 公告列表一页多少条。公告总量小，一页给多点省翻页 */
    private static final int DEFAULT_ANNOUNCEMENT_LIMIT = 20;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NotificationInboxService notificationInboxService;
    private final AnnouncementService announcementService;
    private final NotificationPreferenceService notificationPreferenceService;
    private final MemberService memberService;

    // ------------------------------------------------------------------ 通知 tab

    @Override
    public NotificationPageView page(NotificationPageCmd cmd) {
        MemberNotificationQuery query = new MemberNotificationQuery();
        query.setMemberId(cmd.memberId());
        query.setUnreadOnly(cmd.unreadOnly());
        if (cmd.category() != null && !cmd.category().isBlank()) {
            query.setCategory(NotificationCategoryEnum.valueOf(cmd.category()));
        }

        Page<?> page = new Page<>(
                cmd.pageNum() == null || cmd.pageNum() < 1 ? 1 : cmd.pageNum(),
                cmd.pageSize() == null || cmd.pageSize() < 1 ? 20 : Math.min(cmd.pageSize(), 100));

        List<MemberNotificationDTO> rows = notificationInboxService.queryPage(page, query);
        return new NotificationPageView(
                rows.stream().map(this::toView).toList(),
                page.getTotal(),
                // 未读数和列表在同一次请求里拿：省一次往返，也不会出现
                // 「列表显示 3 条未读、红点写着 5」这种自相矛盾
                notificationInboxService.countUnread(cmd.memberId()));
    }

    @Override
    public NotificationDetailView detail(Long id, Long memberId) {
        MemberNotificationDetailDTO dto = notificationInboxService.detail(id, memberId);
        if (dto == null) {
            // 不存在与不是你的给同一个结果（null），由网关统一翻成 404
            return null;
        }
        return new NotificationDetailView(
                dto.getId(),
                dto.getTemplateCode(),
                dto.getCategory() == null ? null : dto.getCategory().name(),
                dto.getTitle(),
                dto.getContent(),
                dto.getReadFlag(),
                format(dto.getCreateTime()));
    }

    @Override
    public boolean markRead(Long id, Long memberId) {
        return notificationInboxService.markRead(id, memberId);
    }

    @Override
    public int markAllRead(Long memberId) {
        return notificationInboxService.markAllRead(memberId);
    }

    // ------------------------------------------------------------------ 公告 tab

    @Override
    public List<AnnouncementView> announcements(Long memberId, Long lastId, Integer limit) {
        LocalDateTime createTime = memberService.getCreateTime(memberId);
        int size = limit == null || limit < 1 ? DEFAULT_ANNOUNCEMENT_LIMIT : Math.min(limit, 50);
        List<Announcement> rows = announcementService.list(memberId, createTime, size, lastId);

        // 未读判定在服务端做：端上自己比 id 的话，游标语义就散到客户端去了，
        // 而那意味着 iOS / Android / H5 三份实现里迟早有一份写错
        long cursor = announcementService.currentReadCursor(memberId);
        return rows.stream().map(row -> toView(row, cursor)).toList();
    }

    @Override
    public long announcementUnread(Long memberId) {
        return announcementService.countUnread(memberId, memberService.getCreateTime(memberId));
    }

    @Override
    public void readAnnouncement(Long id, Long memberId) {
        announcementService.markRead(memberId, id);
    }

    @Override
    public void readAllAnnouncements(Long memberId) {
        announcementService.markAllRead(memberId);
    }

    @Override
    public List<AnnouncementView> pendingAck(Long memberId) {
        LocalDateTime createTime = memberService.getCreateTime(memberId);
        // 待确认的按定义就是未读，第二个参数给 0 让 unread 恒为 true
        return announcementService.pendingAck(memberId, createTime).stream()
                .map(row -> toView(row, 0L))
                .toList();
    }

    @Override
    public boolean ackAnnouncement(Long id, Long memberId, String ackIp) {
        return announcementService.ack(id, memberId, ackIp);
    }

    // ------------------------------------------------------------------ 免打扰

    @Override
    public NotificationPreferenceView preference(Long memberId) {
        MemberNotificationPreference preference = notificationPreferenceService.get(memberId);
        return new NotificationPreferenceView(
                isOn(preference.getTradeEnabled()),
                isOn(preference.getMarketingEnabled()));
    }

    @Override
    public void savePreference(Long memberId, boolean tradeEnabled, boolean marketingEnabled) {
        notificationPreferenceService.save(memberId, tradeEnabled, marketingEnabled);
    }

    // ------------------------------------------------------------------ 转换

    private NotificationView toView(MemberNotificationDTO dto) {
        return new NotificationView(
                dto.getId(),
                dto.getTemplateCode(),
                dto.getCategory() == null ? null : dto.getCategory().name(),
                dto.getSummary(),
                dto.getReadFlag(),
                format(dto.getCreateTime()));
    }

    private AnnouncementView toView(Announcement row, long readCursor) {
        return new AnnouncementView(
                row.getId(),
                row.getTitle(),
                row.getContent(),
                row.getCategory() == null ? null : row.getCategory().name(),
                row.getForceAck() != null && row.getForceAck() == 1,
                row.getId() > readCursor,
                format(row.getPublishTime()));
    }

    private static boolean isOn(Integer flag) {
        return flag == null || flag == 1;
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : time.format(TIME);
    }
}
