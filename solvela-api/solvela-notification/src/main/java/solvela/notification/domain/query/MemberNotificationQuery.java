package solvela.notification.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import solvela.base.domain.PageParam;
import solvela.enums.NotificationCategoryEnum;

/**
 * 收件箱（通知 tab）分页查询的<b>领域参数</b>。
 *
 * <p>🔴 条件的取法只有一个原则：<b>必须吃得到
 * {@code idx_member_read(member_id, read_flag, id)}。</b>
 * 这张表会长到亿级，一次走不上索引的查询就是一次全表扫。
 *
 * <p>所以这里刻意<b>没有</b>「按标题 / 正文模糊搜」—— 模板化之后正文压根不在这张表里，
 * 想搜也搜不了。真要按内容找，正确做法是按 {@link #templateCode} 加 params 里的字段搜
 * （可索引、更快也更准），不是 LIKE。
 *
 * @Author alaric
 * @Date 2026-09-14
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MemberNotificationQuery extends PageParam {

    /**
     * 会员号。🔴 <b>必填</b>，且必须由服务端从登录态取，绝不能让客户端传 ——
     * 客户端能传就等于能翻别人的收件箱。
     */
    private Long memberId;

    /**
     * 分类过滤，可空（空 = 全部）。对应 C 端的二级筛选。
     */
    private NotificationCategoryEnum category;

    /**
     * 只看未读。空 = 不限。
     *
     * <p>用 Boolean 而不是 Integer：调用方写 {@code setUnreadOnly(true)} 比
     * {@code setReadFlag(0)} 少一次「0 是已读还是未读」的回忆。
     */
    private Boolean unreadOnly;

    /**
     * 模板编码过滤，可空。客服排查用（「这个人收到过发货通知吗」）。
     */
    private String templateCode;
}
