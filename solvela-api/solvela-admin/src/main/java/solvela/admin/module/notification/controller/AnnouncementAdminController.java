package solvela.admin.module.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import solvela.notification.Announcement;
import solvela.notification.service.NotificationAdminService;
import solvela.web.RequiresPermission;

import java.util.List;

/**
 * 公告 Controller。
 *
 * <h3>公告可以原地改，与模板相反</h3>
 * 公告的正文就存在自己那一行里，改了就是改了，不存在「历史消息指向旧版本」的问题。
 *
 * <p>⚠️ 但改一条<b>已发布</b>的公告要谨慎：已经读过它的用户不会再收到提醒
 * （游标已经越过它了）。要让所有人重新看到，应该发一条新公告。
 *
 * <h3>🔴 没有「谁还没确认」这个接口</h3>
 * 强制确认公告只记<b>确认过的</b>人，不给全人群预建「待确认」行 ——
 * 后者就是「广播写扩散」的复刻，行数 = 人群数 × 必读公告数。
 *
 * <p>代价是答不了「谁还没确认」（那是个反连接，人群一大就很贵）。
 * 但业务上不需要：强制确认公告本来就是「没确认就每次进来都弹」，自带催办。
 *
 * @Author alaric
 * @Date 2026-09-14
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "公告")
@RequestMapping("/announcement")
public class AnnouncementAdminController {

    private final NotificationAdminService notificationAdminService;

    @Operation(summary = "公告列表，新的在前")
    @GetMapping("/list")
    @RequiresPermission("announcement:query")
    public List<Announcement> list(@RequestParam(required = false) Integer limit) {
        return notificationAdminService.listAnnouncements(limit);
    }

    @Operation(summary = "详情")
    @GetMapping("/detail")
    @RequiresPermission("announcement:query")
    public Announcement detail(@RequestParam Long id) {
        return notificationAdminService.getAnnouncement(id);
    }

    @Operation(summary = "新增或编辑")
    @PostMapping("/save")
    @RequiresPermission("announcement:save")
    public void save(@RequestBody @Valid Announcement announcement) {
        if (announcement.getExpireTime() == null) {
            // expire_time 是 NOT NULL，而且它同时是未读计算的过滤条件和归档依据。
            // 运营没填时给个默认值，而不是让它撞一个看不懂的数据库错误
            announcement.setExpireTime(NotificationAdminService.defaultExpireTime());
        }
        notificationAdminService.saveAnnouncement(announcement);
    }

    @Operation(summary = "下架（不删除，确认记录还留着）")
    @PostMapping("/offline")
    @RequiresPermission("announcement:save")
    public void offline(@RequestParam Long id) {
        notificationAdminService.offlineAnnouncement(id);
    }

    /**
     * 物理删除，连同确认记录。
     *
     * <p>⚠️ 强制确认公告的确认记录是<b>合规留痕</b>，删之前想清楚。
     * 一般情况下用「下架」就够了。
     */
    @Operation(summary = "删除（连同确认记录，⚠️ 合规留痕会一起没）")
    @PostMapping("/delete")
    @RequiresPermission("announcement:delete")
    public void delete(@RequestParam Long id) {
        notificationAdminService.deleteAnnouncement(id);
    }

    /**
     * 确认人数。
     *
     * <p>⚠️ 分母（当前命中人群的会员数）<b>是动态的</b>：新注册用户会进来。
     * 所以覆盖率不会停在 100%，这是正常的，不是掉了 —— 前端要把这句话写在页面上。
     */
    @Operation(summary = "强制确认公告的确认人数")
    @GetMapping("/ackCount")
    @RequiresPermission("announcement:query")
    public long ackCount(@RequestParam Long id) {
        return notificationAdminService.ackCount(id);
    }
}
