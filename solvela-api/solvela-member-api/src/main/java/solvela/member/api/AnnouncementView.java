package solvela.member.api;

/**
 * 一条公告。
 *
 * <h3>公告的正文直接下发，不像通知那样要渲染</h3>
 * 公告是运营手写的一整段文字，库里存的就是成品 —— 没有模板、没有占位符。
 * 模板化解决的是「同一段文字存 N 遍」的冗余，而公告本来就只有一行。
 *
 * @param forceAck true 表示这是强制确认公告，端上要弹窗而不是塞进列表
 * @param unread   相对于当前会员的游标算出来的。🔴 由服务端算，不要让端上自己比 id
 */
public record AnnouncementView(
        Long id,
        String title,
        String content,
        String category,
        boolean forceAck,
        boolean unread,
        String publishTime) {
}
