package net.lab1024.sa.activity.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动 C 端展示配置。{@link ActivityConfig} 的 1:1 垂直分表。
 *
 * <p><b>为什么不往主表加列</b>：{@code t_activity_config} 是热表 ——
 * C 端每次进活动都要查状态和起止时间。而 {@code ruleContent} 是富文本、几 KB 到几十 KB。
 * InnoDB 一个数据页 16KB，行越宽单页装的行越少；而 MyBatis-Plus 默认 {@code SELECT *}
 * 会把每次状态查询都拖上几十 KB 的规则文本。
 *
 * <p><b>图片一律存 fileId 而不是 URL</b>：存 URL 意味着换 CDN 域名要洗全表、
 * local 切 cloud 要洗全表、想要缩略图拼不出来。将来推 CDN 的 JSON 里需要绝对 URL 时，
 * 走「编辑态存引用、发布态存快照」—— 那样还白送一个好处：已发布的活动页不受后续素材变更影响。
 *
 * @Date 2026-08-10
 */
@Data
@TableName(value = "t_activity_display")
public class ActivityDisplay {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantId;

    private Long activityId;

    private Long mainImageId;

    private Long bgImageId;

    private Long shareImageId;

    private String shareTitle;

    private String shareDesc;

    private String subTitle;

    private String themeColor;

    /**
     * 活动规则，富文本 HTML。<b>禁止 base64 内联图片</b>，见 {@code ActivityDisplayService}。
     */
    private String ruleContent;

    /**
     * 玩法特有、纯透传的配置。
     *
     * <p><b>纪律：后端只存不解析。</b> 一旦开始 {@code JSON_EXTRACT} 取值做业务判断，
     * 它就退化成一堆没有约束的隐式契约，将来谁都不敢动。真需要后端读的，升格成独立列。
     */
    private String extraConfig;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
