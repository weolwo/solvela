package solvela.notification.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.base.json.JsonUtils;
import solvela.base.util.SolvelaTemplateUtil;
import solvela.notification.MemberNotification;
import solvela.notification.NotificationTemplate;
import solvela.notification.dao.MemberNotificationDao;
import solvela.notification.domain.dto.MemberNotificationDTO;
import solvela.notification.domain.dto.MemberNotificationDetailDTO;
import solvela.notification.domain.query.MemberNotificationQuery;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 收件箱读侧：列表、未读数、详情、标记已读。
 *
 * <h3>与 {@code NotificationService}（写侧）刻意分开</h3>
 * 写侧是业务方调的、要考虑事务语义；读侧是 C 端调的、要考虑索引与渲染成本。
 * 两边的变更理由不同，合在一个类里迟早会互相牵制。
 *
 * <h3>只有通知 tab，没有公告</h3>
 * 公告是阶段 4 的事，走另一套读扩散 + 游标，<b>不在这个类里</b>。
 * 前端也是两个 tab、两条独立查询 —— 没有归并、没有 UNION。见方案 §6。
 *
 * @Author alaric
 * @Date 2026-09-14
 * @Copyright weolwo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationInboxService {

    private final MemberNotificationDao memberNotificationDao;
    private final NotificationTemplateService notificationTemplateService;

    /**
     * 收件箱列表。<b>不渲染模板</b> —— 列表页用发送时存好的 summary。
     */
    public List<MemberNotificationDTO> queryPage(Page<?> page, MemberNotificationQuery query) {
        return memberNotificationDao.queryPage(page, query);
    }

    /**
     * 未读数，前端红点用。
     *
     * <p>⚠️ 这只是<b>通知 tab</b> 的未读数。入口总红点 = 它 + 公告 tab 的未读数，
     * 两个数分别算、在端上相加（阶段 4 才有后者）。
     */
    public long countUnread(Long memberId) {
        return memberNotificationDao.countUnread(memberId);
    }

    /**
     * 详情：这里才渲染正文。
     *
     * <p>🔴 必须传 {@code memberId} 做归属校验 —— 只按 id 查的话，
     * 改个数字就能看别人的站内信。
     *
     * @return 不存在或不属于这个会员时返回 {@code null}
     */
    public MemberNotificationDetailDTO detail(Long id, Long memberId) {
        MemberNotification entity = memberNotificationDao.selectById(id);
        if (entity == null || !entity.getMemberId().equals(memberId)) {
            return null;
        }

        MemberNotificationDetailDTO dto = new MemberNotificationDetailDTO();
        dto.setId(entity.getId());
        dto.setTemplateCode(entity.getTemplateCode());
        dto.setCategory(entity.getCategory());
        dto.setReadFlag(entity.getReadFlag());
        dto.setCreateTime(entity.getCreateTime());

        // 🔴 按【发送当时那一版】渲染，不是按最新版。这是模板版本化的全部意义所在
        NotificationTemplate template = notificationTemplateService
                .getByVersion(entity.getTemplateCode(), entity.getTemplateVersion());
        if (template == null) {
            // 模板只停用不删除，所以这里理论上取不到 null。真取到了说明有人物理删过模板行 ——
            // 此时退回 summary 保底，至少别给用户看一片空白
            log.error("【通知】模板版本已不存在，通知:{} 模板:{} v{}。退回 summary 展示",
                    id, entity.getTemplateCode(), entity.getTemplateVersion());
            dto.setTitle("");
            dto.setContent(entity.getSummary());
            return dto;
        }

        Map<String, Object> params = parseParams(entity.getParams());
        dto.setTitle(SolvelaTemplateUtil.render(template.getTitleTemplate(), params));
        dto.setContent(SolvelaTemplateUtil.render(template.getContentTemplate(), params));
        return dto;
    }

    /**
     * 标记一条已读。
     *
     * @return 是否真的改了一行。false = 不存在、不属于这个会员、或本来就是已读
     */
    public boolean markRead(Long id, Long memberId) {
        return memberNotificationDao.markRead(id, memberId, LocalDateTime.now()) > 0;
    }

    /**
     * 全部已读：<b>一次 UPDATE</b>，不是 N 次。
     *
     * @return 本次标记的条数
     */
    public int markAllRead(Long memberId) {
        return memberNotificationDao.markAllRead(memberId, LocalDateTime.now());
    }

    private Map<String, Object> parseParams(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return Map.of();
        }
        try {
            // 用 Map.class 而不是 TypeReference：Jackson 的类型令牌不在本模块的编译路径上，
            // 而为了一个泛型擦除后毫无区别的写法去加一条 jackson-core 依赖不值当。
            // 渲染只需要 Map<String, ?>，运行期拿到的就是 LinkedHashMap<String, Object>
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = JsonUtils.parseObject(paramsJson, Map.class);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            // params 解析失败不该让详情页整个打不开：按空参数渲染，
            // 用户会看到原样保留的 ${xxx}，但至少能看到模板的框架文字
            log.error("【通知】params 解析失败，按空参数渲染: {}", paramsJson, e);
            return Map.of();
        }
    }
}
