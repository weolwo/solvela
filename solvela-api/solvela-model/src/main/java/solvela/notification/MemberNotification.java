package solvela.notification;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import solvela.enums.NotificationCategoryEnum;

import java.time.LocalDateTime;

/**
 * 会员通知（站内信）实体类 —— <b>定向</b>通知，一人一条，写扩散。
 *
 * <h3>为什么不叫 Message</h3>
 * {@code message} 在本仓库已经是 MQ 的词（{@code t_mq_message_log}、{@code MqMessageLog*}、
 * {@code MessageListener}）。再造一个 {@code Message} 实体，以后每次看到这个词
 * 都要先判断是站内信还是队列消息。
 *
 * <h3>🔴 这是全方案里唯一会爆的一张表</h3>
 * 它的行数 = <b>业务事件数 × 时间</b>，只增不减。100 万活跃用户 × 300 条/年 = 3 亿行/年。
 *
 * <p>所以两件事从第一天就必须在：
 * <ul>
 *   <li><b>模板化</b>：只存 {@code templateCode + templateVersion + params}，不存渲染后的全文。
 *       省的不只是硬盘 —— 行宽小了，每个数据页装的行数才多，buffer pool 命中率才不塌。
 *       收件箱是高频读路径。</li>
 *   <li><b>归档</b>：见 {@code MemberNotificationCleanJob}。站内信是时效性内容，
 *       没人翻一年前的「您的订单已发货」。等有了 3 亿行再补归档，第一次跑就是个大删除。</li>
 * </ul>
 *
 * <p>对比一下同方案里的 {@code t_member_announcement_cursor}：那张一人一行，
 * 行数与公告条数无关，发一万条公告也不涨 —— 那才是不会爆的形状。
 *
 * @Author alaric
 * @Date 2026-09-14
 * @Copyright weolwo
 */
@Data
@TableName("t_member_notification")
public class MemberNotification {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会员号：关联键。查询、统计一律用它。
     */
    private Long memberId;

    /**
     * 模板编码，指向 {@code t_notification_template}。
     */
    private String templateCode;

    /**
     * 模板版本：指向发送当时的那一版。
     *
     * <p>🔴 <b>没有这一列，改模板就会追溯篡改历史通知。</b> 详见
     * {@link NotificationTemplate} 的类注释。
     */
    private Integer templateVersion;

    /**
     * 渲染参数，json 对象。
     *
     * <p>🔴 <b>只存显示值，不存 id。</b>
     * <pre>
     * ✅ {"prizeName": "iPhone 15 Pro", "amount": "100"}
     * ❌ {"prizeId": 8812, "amount": "100"}
     * </pre>
     * 存 id 的话渲染时要 join 奖品表，而奖品可能已下架改名 —— 又绕回篡改历史的问题，
     * 还顺带把收件箱查询变成 N+1。
     *
     * <p>这和本项目「单据存快照」的既有约定是一致的（{@code t_mall_order.commodity_name}、
     * {@code t_member_coupon.coupon_name} 都是快照列）。<b>快照防的是「后来的改动篡改历史」，
     * 模板化省的是「同一段文字存 N 遍」，两个目标正交。</b>
     */
    private String params;

    /**
     * 发送时就渲染好的短摘要，列表页直接用。
     *
     * <p>收件箱列表只需要标题 + 摘要 + 时间 + 已读态，<b>不需要渲染全文</b>。
     * 有了这一列，列表页零渲染、零查模板表；只有点进详情才跑一次
     * {@code SolvelaTemplateUtil.render}。
     */
    private String summary;

    /**
     * 模板分类快照：tab 分组与免打扰按它过滤。
     *
     * <p>从模板抄下来而不是每次 join —— 同「单据存快照」的口径。
     */
    private NotificationCategoryEnum category;

    /**
     * 关联业务单号：prize_code / order_no / delivery_id 等。
     *
     * <p>不建索引、不做查询条件，<b>纯排查用</b>：用户说「我没收到发货通知」时，
     * 拿订单号能在这张表里对上。
     */
    private String bizRefId;

    /**
     * 0-未读 1-已读。
     *
     * <p>通知侧的已读是<b>逐条</b>的，所以跳读天然支持、零成本。
     * 公告侧用的是一人一行的游标（不支持跳读），两边刻意分开 ——
     * 前端也是两个 tab，用户不会在同一个列表里看到两种已读行为。
     */
    private Integer readFlag;

    private LocalDateTime readTime;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
