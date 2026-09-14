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
import solvela.notification.NotificationTemplate;
import solvela.notification.service.NotificationAdminService;
import solvela.web.RequiresPermission;

import java.util.List;

/**
 * 通知模板 Controller。
 *
 * <h3>🔴 这里没有「修改」接口，只有「新增版本」</h3>
 * 不是漏了。{@code t_member_notification} 只存「模板编码 + 版本号 + 参数」，
 * 正文是读的时候现渲染的 —— 原地改模板等于<b>追溯篡改所有历史通知</b>：
 * 用户 1 月收到的「恭喜获得 100 积分」，6 月改完模板就变成另一句话。
 * 在金额/奖品类消息上这是事故级的。
 *
 * <p>所以 {@link #save} 永远是新增一版，版本号由服务端算。
 * 前端的「编辑」按钮点开的也是「基于当前版本新建下一版」。
 *
 * <h3>也没有「删除」，只有「停用」</h3>
 * 删掉一行，指向它的历史通知就渲染不出来了 —— 而那些通知是用户真收到过的。
 *
 * @Author alaric
 * @Date 2026-09-14
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "通知模板")
@RequestMapping("/notificationTemplate")
public class NotificationTemplateController {

    private final NotificationAdminService notificationAdminService;

    @Operation(summary = "模板列表（每个编码取最新启用版）")
    @GetMapping("/list")
    @RequiresPermission("notificationTemplate:query")
    public List<NotificationTemplate> list() {
        return notificationAdminService.listLatestTemplates();
    }

    @Operation(summary = "某个编码的全部历史版本，新的在前")
    @GetMapping("/versions")
    @RequiresPermission("notificationTemplate:query")
    public List<NotificationTemplate> versions(@RequestParam String templateCode) {
        return notificationAdminService.listTemplateVersions(templateCode);
    }

    /**
     * 新增一个版本。
     *
     * <p>⚠️ 前端要把「这会新增一版、老版本仍在为历史消息服务」说清楚，
     * 否则运营会以为自己在改一个东西，然后疑惑为什么版本号一直涨。
     */
    @Operation(summary = "新增版本（编辑 = 新增下一版，不是原地改）")
    @PostMapping("/save")
    @RequiresPermission("notificationTemplate:save")
    public int save(@RequestBody @Valid NotificationTemplate template) {
        return notificationAdminService.saveTemplate(template);
    }

    @Operation(summary = "停用某一版（不删除 —— 删了历史通知就渲染不出来）")
    @PostMapping("/disable")
    @RequiresPermission("notificationTemplate:save")
    public void disable(@RequestParam String templateCode, @RequestParam Integer version) {
        notificationAdminService.disableTemplate(templateCode, version);
    }
}
