package solvela.notification.domain.command;

import java.util.List;

/**
 * 人工发送的结果。
 *
 * <p>🔴 <b>逐个会员报成败，不给一个笼统的「成功」</b>：一次发 50 个人，
 * 其中 3 个因为模板缺参数没发出去，只回一个 true 的话运营永远不知道那 3 个人没收到。
 *
 * @param sent   实际发出去的条数
 * @param failed 没发出去的会员号。原因在服务端日志里（{@code NotificationService.send}
 *               吞异常时会带上模板与会员号），这里只报「谁没收到」——
 *               把异常细节回给管理端页面没有意义，运营也看不懂
 */
public record ManualNotifyResult(int sent, List<Long> failed) {

    public boolean allSent() {
        return failed.isEmpty();
    }
}
