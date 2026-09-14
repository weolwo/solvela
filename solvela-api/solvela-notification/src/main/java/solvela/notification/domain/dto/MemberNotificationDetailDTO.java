package solvela.notification.domain.dto;

import lombok.Data;
import solvela.enums.NotificationCategoryEnum;

import java.time.LocalDateTime;

/**
 * 收件箱详情：这里才有渲染后的标题与正文。
 *
 * <p>渲染用的是<b>发送当时那一版</b>模板（{@code template_version} 锁着），
 * 所以运营后来怎么改版，用户回头再看这条通知，措辞和当年一模一样。
 *
 * @Author alaric
 * @Date 2026-09-14
 */
@Data
public class MemberNotificationDetailDTO {

    private Long id;

    private String templateCode;

    private NotificationCategoryEnum category;

    /** 按发送当时的模板版本渲染 */
    private String title;

    /** 按发送当时的模板版本渲染 */
    private String content;

    private Integer readFlag;

    private LocalDateTime createTime;
}
