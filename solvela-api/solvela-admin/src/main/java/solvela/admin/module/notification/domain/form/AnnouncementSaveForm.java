package solvela.admin.module.notification.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import solvela.enums.AnnouncementAudienceEnum;
import solvela.enums.AnnouncementStatusEnum;
import solvela.enums.NotificationCategoryEnum;

import java.time.LocalDateTime;

/**
 * 公告新增 / 编辑的入参。
 *
 * <p>🔴 以前接口直接拿 {@code Announcement} 实体当请求体：请求里带上 createTime / updateTime /
 * createBy 就能原样写进库（那两列本该只由 DDL 产生），而且实体上一条校验都没有，
 * 空标题、空正文只能撞到数据库的 NOT NULL 才被拦下。这里只放运营能填的字段。
 *
 * @Author weolwo
 * @Date 2026-11-20
 */
@Data
public class AnnouncementSaveForm {

    @Schema(description = "公告 ID。空 = 新增")
    private Long id;

    @Schema(description = "标题")
    @NotBlank(message = "标题 必填")
    @Size(max = 128, message = "标题最多 128 个字")
    private String title;

    @Schema(description = "正文。运营手写，不用模板")
    @NotBlank(message = "正文 必填")
    private String content;

    @Schema(description = "分类：只影响 tab 分组与免打扰粒度")
    @NotNull(message = "分类 必选")
    private NotificationCategoryEnum category;

    @Schema(description = "是否强制确认：进 App 就弹窗、必须点「我已阅读」并留痕。不传按普通公告")
    private Boolean forceAck;

    @Schema(description = "人群类型：ALL / REGISTER_BEFORE / REGISTER_AFTER。不传按 ALL；其它值直接拒绝")
    private AnnouncementAudienceEnum audienceType;

    @Schema(description = "人群规则 json，存规则不存名单")
    private String audienceRule;

    @Schema(description = "生效时间")
    @NotNull(message = "生效时间 必填")
    private LocalDateTime publishTime;

    @Schema(description = "失效时间。不填按 30 天兜底")
    private LocalDateTime expireTime;

    @Schema(description = "状态：1-发布 0-下架。不传：新增按发布，编辑不改")
    private AnnouncementStatusEnum status;
}
