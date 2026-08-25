package solvela.activity.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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

    private Long activityId;

    /**
     * 🔴 下面这些业务字段全部标了 {@code updateStrategy = ALWAYS}，<b>少一个都会出静默 bug</b>。
     *
     * <p>MyBatis-Plus 的 {@code updateById} 默认跳过 null 字段，于是「把主视觉移除后保存」
     * 这个动作在库里什么都没发生：界面上图没了、提示「保存成功」，刷新回来图还在，
     * 而且 {@code t_file_relation} 里的引用也没解除（{@code confirm} 收到的仍是旧的 fileId）。
     * 实测踩到过一次 —— 全链路没有任何报错，只有"我明明删了"。
     *
     * <p>本表的保存语义是<b>整表单覆盖</b>：前端每次提交所有字段，null 表示"运营清空了它"，
     * 而不是"这次不改它"。ALWAYS 正是这个语义。
     *
     * <p>⚠️ 注意<b>不要顺手加到 {@code createTime} / {@code updateTime} 上</b> ——
     * 那两列由 DDL 的 CURRENT_TIMESTAMP 产生（铁律 9），标了 ALWAYS 就会被写成 NULL。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long mainImageId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long bgImageId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long shareImageId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String shareTitle;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String shareDesc;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String subTitle;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String themeColor;

    /**
     * 活动规则，富文本 HTML。<b>禁止 base64 内联图片</b>，见 {@code ActivityDisplayService}。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String ruleContent;

    /**
     * 玩法特有、纯透传的配置。
     *
     * <p><b>纪律：后端只存不解析。</b> 一旦开始 {@code JSON_EXTRACT} 取值做业务判断，
     * 它就退化成一堆没有约束的隐式契约，将来谁都不敢动。真需要后端读的，升格成独立列。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String extraConfig;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
