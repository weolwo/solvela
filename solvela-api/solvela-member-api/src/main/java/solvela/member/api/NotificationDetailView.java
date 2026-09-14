package solvela.member.api;

/**
 * 通知详情：这里才有渲染后的标题与正文。
 *
 * <p>渲染用的是<b>发送当时那一版</b>模板（{@code template_version} 锁着），
 * 所以运营后来怎么改版，用户回头再看这条通知，措辞和当年一模一样。
 */
public record NotificationDetailView(
        Long id,
        String templateCode,
        String category,
        String title,
        String content,
        Integer readFlag,
        String createTime) {
}
