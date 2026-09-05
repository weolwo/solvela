package solvela.app.domain;

/**
 * C 端「我的记录」里的一条。
 *
 * @param recordId   记录 id，前端做 key 用
 * @param title      给用户看的标题，就是奖品名
 * @param statusText 给用户看的一句话：发放中 / 已发放 / 发放失败。
 *                   <b>由后端给</b> —— 前端做映射表就是第二份状态机
 * @param status     只用来选颜色，不直接展示：PENDING / DONE / FAILED
 * @param amount     数量/面值，字符串。实物类没有面值时为 null
 * @param createTime 时间，`yyyy-MM-dd HH:mm:ss`
 */
public record RecordView(
        Long recordId,
        String title,
        String statusText,
        String status,
        String amount,
        String createTime) {
}
