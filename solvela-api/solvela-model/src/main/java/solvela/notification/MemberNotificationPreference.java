package solvela.notification;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员的免打扰偏好 —— <b>一人一行</b>。
 *
 * <h3>为什么是一行而不是「一人一分类一行」</h3>
 * 分类只有三档，而且以后也不会多到哪去。一人一行、每档一列，好处是：
 * <ul>
 *   <li>行数 = 用户数，与分类数无关（加一档分类是加一列，不是给每个用户加一行）；</li>
 *   <li>读偏好是一次主键查询，不是一次范围扫描 —— 它在<b>每条通知的发送路径上</b>。</li>
 * </ul>
 *
 * <h3>🔴 没有这一行 = 全部默认开</h3>
 * 和 {@code t_member_announcement_cursor} 一样<b>懒创建</b>：用户第一次改设置才 insert。
 * 从没进过设置页的用户 = 0 行。
 *
 * <p>所以查不到记录时必须当成「都开着」，而不是「都关着」——
 * 反了的话，上线当天全体存量用户就再也收不到任何通知，而且不报错。
 *
 * <h3>SYSTEM 分类没有对应的列</h3>
 * 它不可关（见 {@code NotificationCategoryEnum.mutable}）。不给列是刻意的：
 * 没有列就没人能写进一个「关闭 SYSTEM」的值，这条约束由表结构兜底，
 * 而不是靠每个读的地方都记得判一次。
 *
 * @Author alaric
 * @Date 2026-09-14
 * @Copyright weolwo
 */
@Data
@TableName("t_member_notification_preference")
public class MemberNotificationPreference {

    /** 会员号：一人一行，直接做主键 */
    @TableId
    private Long memberId;

    /** 交易物流类通知：1-接收 0-关闭 */
    private Integer tradeEnabled;

    /** 活动营销类通知：1-接收 0-关闭 */
    private Integer marketingEnabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
