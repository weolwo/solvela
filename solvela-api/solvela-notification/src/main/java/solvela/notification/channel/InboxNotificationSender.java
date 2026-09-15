package solvela.notification.channel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.notification.MemberNotification;
import solvela.notification.dao.MemberNotificationDao;
import solvela.notification.domain.RenderedNotification;
import solvela.notification.spi.NotificationSender;

/**
 * 站内信渠道：把通知落进 {@code t_member_notification}。
 *
 * <h3>只落模板引用，不落正文</h3>
 * 这是全方案里最要紧的一个实现细节：入库的是
 * {@code templateCode + templateVersion + params + summary}，
 * <b>没有渲染后的正文</b>。正文是详情页读的时候现渲染的。
 *
 * <p>为什么值得这么做：这张表的行数 = 业务事件数 × 时间，会长到亿级。
 * 一条「注册成功」正文几百字节且每个用户都一样，存 params 只要几十字节 ——
 * 省 10 倍以上。而真正的收益不是硬盘是 IO：行宽小了，每个数据页装的行数才多，
 * buffer pool 命中率才不塌，而收件箱是高频读路径。
 *
 * <p>{@code summary} 是唯一一个「渲染后才存」的例外，因为列表页要用它，
 * 而列表页的访问频率比详情页高一个量级 —— 为它多存 128 字节是划算的。
 *
 * <h3>🔴 用 Dao 直插，不走 Manager 的批量方法</h3>
 * MyBatis-Plus 的 {@code CrudRepository.saveBatch} / {@code saveOrUpdateBatch} /
 * {@code updateBatchById} 带着 {@code @Transactional(rollbackFor = Exception.class)}，
 * 是事务边界。异常穿过它会把<b>调用方的业务事务</b>标成 rollback-only，
 * 于是「通知插失败」变成「发货也失败」。详见 {@code NotificationService} 的类注释。
 *
 * @Author alaric
 * @Date 2026-09-14
 * @Copyright weolwo
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InboxNotificationSender implements NotificationSender {

    public static final String CHANNEL = "INBOX";

    private final MemberNotificationDao memberNotificationDao;

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public void send(RenderedNotification notification) {
        MemberNotification entity = new MemberNotification();
        entity.setMemberId(notification.memberId());
        entity.setTemplateCode(notification.templateCode());
        entity.setTemplateVersion(notification.templateVersion());
        entity.setParams(notification.paramsJson());
        entity.setSummary(notification.summary());
        entity.setCategory(notification.category());
        entity.setBizRefId(notification.bizRefId());
        entity.setReadFlag(0);
        // 人工发送才有操作人。系统发送留空 —— 那一列为空正好等于「不是人发的」，
        // 事后查人工发过什么就是一句 where create_by is not null
        entity.setCreateBy(notification.operator());
        // create_time / update_time 走 DDL 的 DEFAULT CURRENT_TIMESTAMP，
        // 不在这里取 JVM 时钟 —— 同一条记录的时间应该只有一个来源

        memberNotificationDao.insert(entity);

        if (log.isDebugEnabled()) {
            log.debug("【站内信】已投递，会员:{} 模板:{} v{} 业务单号:{}",
                    notification.memberId(), notification.templateCode(),
                    notification.templateVersion(), notification.bizRefId());
        }
    }
}
