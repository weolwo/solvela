package solvela.notification.domain.dto;

import lombok.Data;
import solvela.enums.NotificationCategoryEnum;

import java.time.LocalDateTime;

/**
 * 收件箱列表的一行。
 *
 * <h3>🔴 这里没有 content</h3>
 * 列表页只需要标题 + 摘要 + 时间 + 已读态。正文要查模板、要渲染，
 * 而列表一页几十条 —— 那就是几十次模板查询加几十次渲染，全是白干的。
 *
 * <p>正文在详情页按需渲染，见 {@code NotificationInboxService#detail}。
 *
 * <h3>title 也不在这里</h3>
 * 同理：标题也是模板渲染出来的。列表页用 {@link #summary}（发送时就渲染好、直接存库的
 * 那 128 字）就够了 —— 它本身已经是可读的一句话。
 *
 * <p>真需要列表也显示标题的话，正确做法是<b>再加一个 {@code title} 存储列</b>
 * （和 summary 一样发送时渲染好），而不是在列表查询里去 join 模板表。
 *
 * @Author alaric
 * @Date 2026-09-14
 */
@Data
public class MemberNotificationDTO {

    private Long id;

    private String templateCode;

    private NotificationCategoryEnum category;

    /** 发送时渲染好的短摘要，列表页直接展示 */
    private String summary;

    /** 0-未读 1-已读 */
    private Integer readFlag;

    private LocalDateTime readTime;

    private LocalDateTime createTime;
}
