package solvela.notification.spi;

import solvela.notification.domain.RenderedNotification;

/**
 * 触达渠道。今天只有一个实现（站内信），留这个接口是为了让第二个渠道是「加一个类」
 * 而不是「改所有调用点」。
 *
 * <h3>渠道拿到的是已渲染好的内容，不是模板</h3>
 * 模板查询、版本锁定、参数渲染、频控、免打扰 —— 全在
 * {@code NotificationService} 那一层做完了。渠道只负责<b>送出去</b>。
 *
 * <p>这样切的好处：加短信渠道时不需要再理解一遍模板版本化那套东西；
 * 坏处是渠道无法做「同一内容按渠道给不同措辞」——真需要时再给
 * {@link RenderedNotification} 加按渠道的变体，别让渠道自己去查模板。
 *
 * <h3>🔴 实现必须自己吞掉异常吗？不是</h3>
 * 渠道<b>应该</b>把失败抛出来，由编排层统一决定怎么处理。
 * 站内信渠道的失败会让业务事务回滚（这是对的，见 {@code NotificationService}）；
 * 将来的 Push / 短信渠道失败则不该影响业务 —— 那个差异由编排层表达，
 * 不要让每个渠道各自猜。
 *
 * @Author alaric
 * @Date 2026-09-14
 */
public interface NotificationSender {

    /**
     * 渠道标识，日志与将来的按渠道开关用。
     */
    String channel();

    /**
     * 送出一条已渲染好的通知。
     */
    void send(RenderedNotification notification);
}
