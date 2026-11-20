package solvela.admin.module.notification.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import solvela.enums.NotificationCategoryEnum;

/**
 * 通知模板「新增一版」的入参。
 *
 * <p>🔴 以前接口直接拿 {@code NotificationTemplate} 实体当请求体：请求里带上 createTime / createBy 就能原样写进库，
 * 也能带上 status = 0 存出一个一出生就停用的版本，而实体上一条校验都没有。这里只放运营能填的字段：
 * <ul>
 *   <li>{@code version} 由服务端取「当前最大版本 + 1」，不收；</li>
 *   <li>{@code status} 不收：新版本一律启用（DDL 默认值），停用走单独的「停用」接口。
 *       管理端表单本来也没有这个选项。</li>
 * </ul>
 *
 * @Author weolwo
 * @Date 2026-11-20
 */
@Data
public class NotificationTemplateSaveForm {

    @Schema(description = "模板编码：跨环境稳定，代码引用它。取值见 NotificationTemplateEnum")
    @NotBlank(message = "模板编码 必选")
    @Size(max = 64, message = "模板编码最多 64 个字符")
    private String templateCode;

    @Schema(description = "分类：只影响 C 端 tab 分组与免打扰粒度")
    @NotNull(message = "分类 必选")
    private NotificationCategoryEnum category;

    @Schema(description = "标题模板，${key} 占位符")
    @NotBlank(message = "标题模板 必填")
    @Size(max = 128, message = "标题模板最多 128 个字")
    private String titleTemplate;

    @Schema(description = "正文模板，${key} 占位符")
    @NotBlank(message = "正文模板 必填")
    private String contentTemplate;

    @Schema(description = "本版本用到的占位符清单，json 数组。发送时校验用")
    private String paramKeys;
}
