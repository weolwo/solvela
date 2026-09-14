package solvela.notification;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import solvela.enums.NotificationCategoryEnum;

import java.time.LocalDateTime;

/**
 * 公告 —— <b>一条内容一行</b>，读扩散。
 *
 * <h3>🔴 这张表和 {@link MemberNotification} 的区别是整个方案的地基</h3>
 * 通知是定向的（一人一条，写扩散）；公告是广播的，<b>绝不能给每个用户插一行</b>。
 * 10 万用户 × 100 条公告 = 1000 万行，而真实信息量只有 100 条。
 *
 * <p>行业里这类系统炸库的经典姿势就这一个，而且故障现象出现在存储层、
 * 根因在写入模型 —— 排查通常停在「数据库不行」，然后换一个更贵的数据库。
 *
 * <p>判据：<b>看行数增长的斜率</b>。用户数翻 10 倍、行数跟着翻 10 倍是正常的；
 * 翻 100 倍就是设计缺陷，换什么库都一样。本表的行数只跟<b>公告条数</b>走。
 *
 * <p>用户的已读状态存在 {@link MemberAnnouncementCursor}，一人一行，与公告条数无关。
 *
 * @Author alaric
 * @Date 2026-09-14
 * @Copyright weolwo
 */
@Data
@TableName("t_announcement")
public class Announcement {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    /**
     * 正文。<b>运营手写，不用模板。</b>
     *
     * <p>模板化解决的是「同一段文字存 N 遍」的冗余，而公告本来就只有一行，
     * 没有冗余可省。给公告也套一层模板只会凭空多一层间接。
     */
    private String content;

    /** 分类：只影响 tab 分组与免打扰粒度 */
    private NotificationCategoryEnum category;

    /**
     * 0-普通公告 1-强制确认。
     *
     * <p>强制确认的公告进 App 就弹窗、必须点「我已阅读」，并在
     * {@link AnnouncementAck} 里留痕。那是整个设计里<b>唯一一个「广播但要写到人」</b>
     * 的例外 —— 但也只写确认记录，不写内容副本。
     */
    private Integer forceAck;

    /** 人群类型：ALL / REGISTER_BEFORE / REGISTER_AFTER */
    private String audienceType;

    /**
     * 人群规则，json。
     *
     * <p>🔴 <b>存规则，不存名单。</b> 一旦把人群物化成名单，行数就又回到
     * 「用户数 × 公告数」，前面所有设计白做。判定放在读路径上。
     */
    private String audienceRule;

    private LocalDateTime publishTime;

    /**
     * 失效时间。
     *
     * <p>🔴 <b>必填，不允许 null。</b> 它同时承担两件事：未读计算的过滤条件、
     * 公告表归档的依据。允许为空等于两条全废 —— 未读数要扫全部历史公告，
     * 而且这张表永远不能归档。
     */
    private LocalDateTime expireTime;

    /** 1-发布 0-下架 */
    private Integer status;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
