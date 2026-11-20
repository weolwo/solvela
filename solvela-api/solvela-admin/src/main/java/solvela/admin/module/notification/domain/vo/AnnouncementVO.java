package solvela.admin.module.notification.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import solvela.enums.AnnouncementAudienceEnum;
import solvela.enums.AnnouncementStatusEnum;
import solvela.enums.NotificationCategoryEnum;

import java.time.LocalDateTime;

/**
 * 公告列表 / 详情的出参。字段与改造前直接返回的实体一一对应，管理端拿到的 JSON 不变。
 *
 * @Author weolwo
 * @Date 2026-11-20
 */
@Data
public class AnnouncementVO {

    private Long id;

    private String title;

    private String content;

    private NotificationCategoryEnum category;

    @Schema(description = "是否强制确认")
    private Boolean forceAck;

    @Schema(description = "人群类型：ALL / REGISTER_BEFORE / REGISTER_AFTER")
    private AnnouncementAudienceEnum audienceType;

    @Schema(description = "人群规则 json")
    private String audienceRule;

    private LocalDateTime publishTime;

    private LocalDateTime expireTime;

    @Schema(description = "状态：1-发布 0-下架")
    private AnnouncementStatusEnum status;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
