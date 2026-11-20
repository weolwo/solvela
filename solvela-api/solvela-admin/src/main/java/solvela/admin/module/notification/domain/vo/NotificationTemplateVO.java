package solvela.admin.module.notification.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import solvela.enums.EnableStatusEnum;
import solvela.enums.NotificationCategoryEnum;

import java.time.LocalDateTime;

/**
 * 通知模板列表 / 历史版本的出参。字段与改造前直接返回的实体一一对应，管理端拿到的 JSON 不变
 * （{@code status} 是枚举，序列化出来仍是 1 / 0）。
 *
 * @Author weolwo
 * @Date 2026-11-20
 */
@Data
public class NotificationTemplateVO {

    private String templateCode;

    private Integer version;

    private NotificationCategoryEnum category;

    private String titleTemplate;

    private String contentTemplate;

    @Schema(description = "本版本用到的占位符清单，json 数组")
    private String paramKeys;

    @Schema(description = "状态：1-启用 0-停用")
    private EnableStatusEnum status;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
