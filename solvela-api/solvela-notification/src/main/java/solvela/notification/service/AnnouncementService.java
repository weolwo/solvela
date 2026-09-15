package solvela.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.notification.Announcement;
import solvela.notification.MemberAnnouncementCursor;
import solvela.notification.dao.AnnouncementAckDao;
import solvela.notification.dao.AnnouncementDao;
import solvela.notification.dao.MemberAnnouncementCursorDao;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公告：读侧 + 已读推进 + 强制确认。
 *
 * <h3>🔴 与 {@code NotificationInboxService} 是两条独立的路径，没有归并</h3>
 * 前端是<b>两个 tab</b>：通知一个、公告一个，各查各的、各自分页。
 *
 * <p>早期方案写的是「两路各自有序、内存归并成一个收件箱列表」，还特别强调
 * 不要用 SQL UNION 分页（两张表的过滤条件完全不同，UNION 之后排序分页会让
 * 两边的索引都用不上）。<b>分 tab 之后这段复杂度整个消失了</b> ——
 * 这是「两个 tab」这个产品决定顺带买到的最大一笔简化。
 *
 * <p>入口总红点 = 两个 tab 的未读数相加，在端上做。
 *
 * @Author alaric
 * @Date 2026-09-14
 * @Copyright weolwo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementDao announcementDao;
    private final MemberAnnouncementCursorDao memberAnnouncementCursorDao;
    private final AnnouncementAckDao announcementAckDao;

    /**
     * 公告 tab 列表。
     *
     * @param lastId 上一页最后一条的 id，首页传 null。用游标翻页而不是 offset ——
     *               公告会持续新增，offset 分页在第 2 页会重复看到被挤下来的那一条
     */
    public List<Announcement> list(Long memberId, LocalDateTime memberCreateTime, int limit, Long lastId) {
        return announcementDao.selectVisible(LocalDateTime.now(), memberCreateTime, limit, lastId);
    }

    /**
     * 公告 tab 的未读数。
     *
     * <p>⚠️ 这只是公告那一半。入口总红点还要加上
     * {@code NotificationInboxService.countUnread}，两个数在端上相加。
     */
    public long countUnread(Long memberId, LocalDateTime memberCreateTime) {
        return announcementDao.countUnread(LocalDateTime.now(), memberCreateTime, currentCursor(memberId));
    }

    /**
     * 读了某一条：把游标推到它。
     *
     * <p>🔴 <b>不支持跳读</b>是刻意的：点开任何一条，它<b>之前</b>的全部算已读。
     * 所以游标只需要一个 bigint，没有例外集合、没有需要定期收敛的 JSON 列。
     *
     * <p>成立的前提是前端把公告和通知分成<b>两个 tab</b> —— 两种已读语义各待在
     * 自己的列表里，用户不会在同一个列表看到「点通知清一条、点公告清一片」。
     * 哪天有人要把两个 tab 合并，必须回来重新评估这里。
     */
    public void markRead(Long memberId, Long announcementId) {
        memberAnnouncementCursorDao.advance(memberId, announcementId);
    }

    /**
     * 全部已读：把游标推到<b>当前可见公告的最大 id</b>。
     *
     * <p>一次 UPDATE，不是 N 次 —— 这是游标模型白送的。
     *
     * <p>推到「当前可见的最大 id」而不是「表里的最大 id」：后者会把还没到
     * {@code publish_time} 的预发布公告一起标成已读，那些公告发布后用户就再也看不到红点了。
     */
    public void markAllRead(Long memberId) {
        Long maxId = announcementDao.selectMaxVisibleId(LocalDateTime.now());
        if (maxId != null) {
            memberAnnouncementCursorDao.advance(memberId, maxId);
        }
    }

    /**
     * 进 App 时查：有没有必须先确认才能继续用的公告。
     *
     * <p>返回非空就弹窗，用户点了「我已阅读」再调 {@link #ack}。
     * 没确认就一直返回 —— <b>这个机制自带催办</b>，所以我们不需要
     * 「谁还没确认」那种反连接查询。
     */
    public List<Announcement> pendingAck(Long memberId, LocalDateTime memberCreateTime) {
        return announcementDao.selectPendingAck(LocalDateTime.now(), memberId, memberCreateTime);
    }

    /**
     * 用户点了「我已阅读并知悉」。
     *
     * <p>🔴 <b>只在这里写 ack 行</b>。绝不要在公告发布时给全人群预建待确认行 ——
     * 那是「广播写扩散」的复刻，行数 = 人群数 × 必读公告数。
     *
     * <h3>🔴 确认之后必须把游标也推过去</h3>
     * 「确认留痕」和「已读游标」是<b>两套状态</b>：前者在 {@code t_announcement_ack}，
     * 后者在 {@code t_member_announcement_cursor}。2026-09-15 之前这里只写了留痕、
     * 没推游标，于是用户在弹窗上点完「我已阅读并知悉」，回到公告 tab 那条<b>还是未读</b>
     * —— 两个事实对不上，而红点在说谎。
     *
     * <p>修法不是给 unread 再加一条「acked 也算已读」的规则（那会让未读语义
     * 变成「游标 OR ack」两套并存），而是认下一件事：<b>确认这个动作本身就蕴含
     * 「我读过了」</b>，所以它走和 {@link #markRead} 完全一样的那条路。
     *
     * <p>⚠️ 随之而来的是游标模型固有的性质：推到这一条，<b>比它更旧的公告也一并算已读</b>。
     * 这和用户在列表里点开它是同一个结果 —— 整个设计从一开始就不支持跳读。
     *
     * @param ackIp 确认时 IP，合规场景可能要。拿不到就传 null，不要为它编一个值
     * @return 是否是第一次确认（false = 重复点击，已被主键挡住）
     */
    public boolean ack(Long announcementId, Long memberId, String ackIp) {
        boolean first = announcementAckDao.ack(announcementId, memberId, LocalDateTime.now(), ackIp) > 0;

        // 🔴 无论是不是第一次都要推：重复点击时 ack 被主键挡住返回 0，
        //    但游标可能还没推过去（比如上一次推游标那步失败了）。
        //    advance 自己是幂等的（GREATEST，只前进不后退），多调一次无害。
        memberAnnouncementCursorDao.advance(memberId, announcementId);

        return first;
    }

    /**
     * 某条强制确认公告的确认人数。运营看覆盖率用。
     *
     * <p>⚠️ 分母要单独算（当前命中人群的会员数），而且<b>人群是动态的</b>：
     * 新注册用户会进入分母，所以覆盖率不会停在 100%。跟运营讲清楚这一点，
     * 否则他们会以为「覆盖率掉了」。
     */
    public long ackCount(Long announcementId) {
        return announcementAckDao.countByAnnouncement(announcementId);
    }

    /**
     * 当前已读游标。端侧要拿它给每条公告标 unread。
     *
     * <p>🔴 未读判定放在<b>服务端</b>做，不要把游标下发给端上自己比 id ——
     * 那等于把游标语义散到 iOS / Android / H5 三份实现里，迟早有一份写错，
     * 而写错的表现是「红点数和列表对不上」，没人会怀疑到客户端。
     */
    public long currentReadCursor(Long memberId) {
        return currentCursor(memberId);
    }

    /** 没有游标行 = 从没读过 = 全部未读。🔴 绝不能反过来当成「全部已读」 */
    private long currentCursor(Long memberId) {
        MemberAnnouncementCursor cursor = memberAnnouncementCursorDao.selectById(memberId);
        return cursor == null || cursor.getLastReadId() == null ? 0L : cursor.getLastReadId();
    }
}
