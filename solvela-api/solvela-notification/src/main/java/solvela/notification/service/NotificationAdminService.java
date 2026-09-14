package solvela.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.notification.Announcement;
import solvela.notification.NotificationTemplate;
import solvela.notification.dao.AnnouncementAckDao;
import solvela.notification.dao.AnnouncementDao;
import solvela.notification.dao.NotificationTemplateDao;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端用：模板与公告的维护。
 *
 * <h3>🔴 模板「编辑」= 新增一个版本，不是 UPDATE</h3>
 * 这是本类最重要的一条，而且它<b>必须由服务端强制</b>，不能指望管理端页面自觉。
 *
 * <p>{@code t_member_notification} 只存「模板编码 + 版本号 + 参数」，正文是读的时候
 * 现渲染的。原地改模板 = <b>所有历史通知的显示被追溯篡改</b>：用户 1 月收到的
 * 「恭喜获得 100 积分」，6 月改完模板就变成另一句话了。在金额/奖品类消息上
 * 这是事故级的 —— 用户截图的和现在显示的对不上。
 *
 * <p>所以 {@link #saveTemplate} 永远是 insert，版本号由服务端算。
 * <b>本类没有 updateTemplate，也不该有。</b>
 *
 * @Author alaric
 * @Date 2026-09-14
 * @Copyright weolwo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationAdminService {

    private final NotificationTemplateDao notificationTemplateDao;
    private final AnnouncementDao announcementDao;
    private final AnnouncementAckDao announcementAckDao;

    // ------------------------------------------------------------------ 模板

    /** 某个编码的全部版本，新的在前。运营看「改过几次、当年那版长什么样」 */
    public List<NotificationTemplate> listTemplateVersions(String templateCode) {
        LambdaQueryWrapper<NotificationTemplate> wrapper = new LambdaQueryWrapper<NotificationTemplate>()
                .eq(NotificationTemplate::getTemplateCode, templateCode)
                .orderByDesc(NotificationTemplate::getVersion);
        return notificationTemplateDao.selectList(wrapper);
    }

    /** 全部启用中的模板，每个编码取最新版。模板列表页用 */
    public List<NotificationTemplate> listLatestTemplates() {
        return notificationTemplateDao.selectAllEnabled().reversed().stream()
                // selectAllEnabled 按 (code, version) 升序，反转后同 code 的新版在前，
                // distinct 取到的就是每个 code 的最新版
                .filter(distinctByCode())
                .toList();
    }

    /**
     * 保存模板 = <b>新增一个版本</b>。
     *
     * <p>🔴 永远是 insert。版本号取「当前最大版本 + 1」，由服务端算而不是让页面传 ——
     * 页面传的话，两个人同时编辑就会撞版本号，而撞了的表现是一个
     * {@code DuplicateKeyException} 或者更糟：后保存的那份悄悄覆盖了前一份。
     *
     * @return 新版本号
     */
    public int saveTemplate(NotificationTemplate template) {
        Integer maxVersion = notificationTemplateDao.selectMaxVersion(template.getTemplateCode());
        int next = maxVersion == null ? 1 : maxVersion + 1;
        template.setVersion(next);
        notificationTemplateDao.insert(template);
        log.info("【通知模板】{} 新增版本 v{}", template.getTemplateCode(), next);
        return next;
    }

    /**
     * 停用某一版。
     *
     * <p>⚠️ <b>只停用不删除。</b> 删掉一行，所有指向它的历史通知就渲染不出来了 ——
     * 而那些通知是用户真收到过的，不该因为运营下线了一个模板就变成空白。
     * 所以这里没有 delete 方法。
     */
    public void disableTemplate(String templateCode, Integer version) {
        // 🔴 不能用 updateById：本实体是复合主键，那个基类方法根本生成不出来，
        //    调了会在运行期抛 Invalid bound statement。详见 Dao 上 disableVersion 的注释
        notificationTemplateDao.disableVersion(templateCode, version);
    }

    // ------------------------------------------------------------------ 公告

    public List<Announcement> listAnnouncements(Integer limit) {
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<Announcement>()
                .orderByDesc(Announcement::getId)
                .last("LIMIT " + (limit == null || limit < 1 ? 50 : Math.min(limit, 200)));
        return announcementDao.selectList(wrapper);
    }

    public Announcement getAnnouncement(Long id) {
        return announcementDao.selectById(id);
    }

    /**
     * 新增或编辑公告。
     *
     * <p>公告<b>可以原地改</b>，与模板相反 —— 因为它的正文就存在自己这一行里，
     * 改了就是改了，不存在「历史消息指向旧版本」的问题。
     *
     * <p>⚠️ 但改一条<b>已发布</b>的公告要谨慎：已经读过它的用户不会再收到提醒
     * （游标已经越过它了）。要让所有人重新看到，应该发一条新公告。
     */
    public void saveAnnouncement(Announcement announcement) {
        if (announcement.getId() == null) {
            announcementDao.insert(announcement);
        } else {
            announcementDao.updateById(announcement);
        }
    }

    /**
     * 下架一条公告（{@code status = 0}）。
     *
     * <p>不物理删除：{@code t_announcement_ack} 里可能有它的确认记录，
     * 而那是合规留痕。真要清理走 {@link #deleteAnnouncement}。
     */
    public void offlineAnnouncement(Long id) {
        Announcement update = new Announcement();
        update.setId(id);
        update.setStatus(0);
        announcementDao.updateById(update);
    }

    /**
     * 物理删除一条公告，<b>连同它的确认记录</b>。
     *
     * <p>🔴 顺序不能反：先删 ack 再删公告。反过来的话，中途失败会留下一堆
     * 指向不存在公告的孤儿 ack 行 —— 那些行永远查不到，只是白占空间，
     * 还会让「确认人数」统计对不上。
     *
     * <p>⚠️ 强制确认公告的 ack 是<b>合规留痕</b>，删之前想清楚。
     */
    public void deleteAnnouncement(Long id) {
        announcementAckDao.deleteByAnnouncement(id);
        announcementDao.deleteById(id);
        log.info("【公告】{} 及其确认记录已删除", id);
    }

    /**
     * 某条强制确认公告的确认人数。
     *
     * <p>⚠️ <b>没有「谁还没确认」</b>，那是刻意的：它 = 目标人群 ➖ ack 集合，
     * 是个反连接，人群一大就很贵；要让它快就得物化人群名单，
     * 而那正是整个设计要避免的 N×M。
     *
     * <p>业务上也不需要 —— 强制确认公告本来就是「没确认就每次进来都弹」，自带催办。
     */
    public long ackCount(Long announcementId) {
        return announcementAckDao.countByAnnouncement(announcementId);
    }

    private static java.util.function.Predicate<NotificationTemplate> distinctByCode() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        return template -> seen.add(template.getTemplateCode());
    }

    /** 公告的默认失效时间：没填时给 30 天，而不是留空 —— expire_time 是 NOT NULL */
    public static LocalDateTime defaultExpireTime() {
        return LocalDateTime.now().plusDays(30);
    }
}
