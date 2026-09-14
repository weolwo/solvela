package solvela.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.base.json.JsonUtils;
import solvela.base.util.SolvelaTemplateUtil;
import solvela.enums.NotificationTemplateEnum;
import solvela.notification.NotificationTemplate;
import solvela.notification.domain.NotifyRequest;
import solvela.notification.domain.RenderedNotification;
import solvela.notification.spi.NotificationSender;

import java.util.List;
import java.util.Map;

/**
 * 触达编排层 —— 业务方唯一的入口。
 *
 * <h3>它做什么</h3>
 * <ol>
 *   <li>查模板（锁定当前启用版本）</li>
 *   <li>校验参数完整性</li>
 *   <li>渲染标题 / 正文 / 摘要</li>
 *   <li>（阶段 3）免打扰过滤、频控与合并</li>
 *   <li>路由到各渠道</li>
 * </ol>
 *
 * <h3>🔴 事务语义：同步、进调用方的事务，但自身失败不反噬业务</h3>
 * 两条要求看着矛盾，其实是两个方向：
 *
 * <ul>
 *   <li><b>业务回滚 → 通知跟着回滚。</b> 所以是同步、同事务，不是
 *       {@code @TransactionalEventListener(AFTER_COMMIT)}。发货事务最后失败了
 *       还留着一条「您的订单已发货」，是错的。</li>
 *   <li><b>通知失败 ✗→ 业务回滚。</b> 模板没配、渲染出错，不该让发货发不出去。
 *       所以 {@link #send} <b>吞掉所有异常只记日志</b>，永不上抛。</li>
 * </ul>
 *
 * <p>⚠️ 能同时满足是有前提的，写代码时必须守住：
 * <b>发送路径上不许出现任何 {@code @Transactional} 边界。</b>
 * 异常在 {@code @Transactional} 方法边界上传播时，Spring 的 TransactionInterceptor
 * 会把<b>当前事务</b>（传播 REQUIRED 时就是调用方的事务）标成 rollback-only，
 * 之后业务提交会炸 {@code UnexpectedRollbackException} —— 那就变成
 * 「通知失败把发货也搞挂了」，正好是我们要避免的。
 *
 * <p>具体到代码：{@code InboxNotificationSender} 用 {@code MemberNotificationDao.insert()}
 * 直插，<b>不走 {@code MemberNotificationManager.saveBatch()}</b> ——
 * MyBatis-Plus 的 {@code CrudRepository} 里 {@code saveBatch} /
 * {@code saveOrUpdateBatch} / {@code updateBatchById} 这三个方法带着
 * {@code @Transactional(rollbackFor = Exception.class)}，是货真价实的事务边界。
 * 单条 {@code save()} 没有，但直接用 Dao 更不容易踩错。
 *
 * @Author alaric
 * @Date 2026-09-14
 * @Copyright weolwo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /**
     * 摘要最大长度，对齐 {@code t_member_notification.summary} 的 varchar(128)。
     *
     * <p>超长直接截断而不是报错：摘要是给列表页看一眼的，截了不影响正确性；
     * 而为一条摘要太长就不发通知，是拿次要目标伤害主要目标。
     */
    private static final int SUMMARY_MAX_LENGTH = 128;

    private final NotificationTemplateService notificationTemplateService;
    private final NotificationPreferenceService notificationPreferenceService;

    /**
     * 所有已注册的渠道。阶段 1 只有站内信一个。
     *
     * <p>Spring 会把容器里全部 {@link NotificationSender} 注进来 ——
     * 加第二个渠道就是新增一个 {@code @Component}，这里一行都不用改。
     */
    private final List<NotificationSender> senders;

    /**
     * 发一条通知。
     *
     * <p>🔴 <b>永不抛异常。</b> 调用方不需要 try-catch，也不该根据返回值做业务判断 ——
     * 返回 false 只表示「这次没发出去」，不表示业务失败。
     *
     * @return 是否至少有一个渠道送达
     */
    public boolean send(NotifyRequest request) {
        try {
            return doSend(request);
        } catch (Exception e) {
            // 通知不是业务的一部分，失败只记日志。带上模板与会员号，否则这条日志没法排查
            log.error("【通知】发送失败，模板:{} 会员:{} 业务单号:{}",
                    request.template(), request.memberId(), request.bizRefId(), e);
            return false;
        }
    }

    private boolean doSend(NotifyRequest request) {
        NotificationTemplateEnum templateEnum = request.template();

        NotificationTemplate template = notificationTemplateService.getLatestEnabled(templateEnum.getCode());
        if (template == null) {
            // 模板没配是配置问题，不是代码问题 —— 但它会让用户静默收不到通知，
            // 所以必须是 error 级别，而不是 warn 之后就没人管了
            log.error("【通知】模板未配置或已全部停用，模板:{} 会员:{}", templateEnum.getCode(), request.memberId());
            return false;
        }

        Map<String, Object> params = request.params();
        warnOnMissingParams(templateEnum, template, params);

        String title = SolvelaTemplateUtil.render(template.getTitleTemplate(), params);
        String content = SolvelaTemplateUtil.render(template.getContentTemplate(), params);

        RenderedNotification rendered = new RenderedNotification(
                request.memberId(),
                templateEnum,
                template.getTemplateCode(),
                template.getVersion(),
                template.getCategory(),
                title,
                content,
                toSummary(content),
                JsonUtils.toJson(params),
                request.bizRefId());

        // ------------------------------------------------------------------
        // 免打扰：用户关掉了这个 category 就到此为止。
        //
        // 🔴 这道闸放在编排层，不是让每个业务方各判一遍 —— 散出去写，
        //    第二个人一定会漏，而漏了不报错、只是用户关了还继续收到。
        //
        // ⚠️ 位置很讲究：放在【渲染之后、投递之前】。
        //    放渲染之前能省一次渲染，但那样「模板缺参数」这类问题就只在
        //    没开免打扰的用户身上才会暴露 —— 排查时会看到一个诡异的
        //    「只有部分用户的通知是坏的」。宁可多渲染一次。
        // ------------------------------------------------------------------
        if (!notificationPreferenceService.accepts(request.memberId(), rendered.category())) {
            if (log.isDebugEnabled()) {
                log.debug("【通知】被免打扰拦下，会员:{} 模板:{} 分类:{}",
                        request.memberId(), templateEnum.getCode(), rendered.category());
            }
            return false;
        }

        boolean anyDelivered = false;
        for (NotificationSender sender : senders) {
            sender.send(rendered);
            anyDelivered = true;
        }
        return anyDelivered;
    }

    /**
     * 缺参数只告警、不拦截。
     *
     * <p>拦截了的后果是「模板加了个新占位符 → 所有老调用方的通知全发不出去」，
     * 比「通知里有一处 {@code ${xxx}} 没替换掉」严重得多。
     *
     * <p>但它<b>必须留痕</b>：{@code SolvelaTemplateUtil} 解析不到的占位符是原样保留的，
     * 用户会直接看到字面的 {@code ${logisticsNo}}。没有这条日志就只能等用户来投诉。
     */
    private void warnOnMissingParams(NotificationTemplateEnum templateEnum,
                                     NotificationTemplate template,
                                     Map<String, Object> params) {
        // 库里的 param_keys 跟着 version 走，是权威；枚举那份是编译期文档。
        // 库里没填时退回枚举，总比不校验强
        List<String> expected = parseParamKeys(template.getParamKeys());
        if (expected.isEmpty()) {
            expected = templateEnum.getRequiredParams();
        }
        List<String> missing = expected.stream().filter(key -> !params.containsKey(key)).toList();
        if (!missing.isEmpty()) {
            log.warn("【通知】模板 {} v{} 缺少占位符参数 {}，这些位置会原样显示 ${{...}} 给用户",
                    template.getTemplateCode(), template.getVersion(), missing);
        }
    }

    private List<String> parseParamKeys(String paramKeysJson) {
        if (paramKeysJson == null || paramKeysJson.isBlank()) {
            return List.of();
        }
        try {
            List<String> keys = JsonUtils.parseList(paramKeysJson, String.class);
            return keys == null ? List.of() : keys;
        } catch (Exception e) {
            // param_keys 是运营可改的自由文本，解析失败不该影响发送
            log.warn("【通知】param_keys 解析失败，按「不校验」处理: {}", paramKeysJson);
            return List.of();
        }
    }

    /**
     * 从正文裁出列表页用的摘要。
     *
     * <p>发送时算一次存起来，收件箱列表就不用查模板、不用渲染 —— 这是那条高频读路径
     * 能完全不碰模板表的原因。
     */
    private String toSummary(String content) {
        if (content == null) {
            return "";
        }
        // 换行在一行摘要里是噪声，压成空格；连续空白再压一次，否则富文本正文会裁出一堆空格
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() <= SUMMARY_MAX_LENGTH ? flat : flat.substring(0, SUMMARY_MAX_LENGTH - 1) + "…";
    }
}
