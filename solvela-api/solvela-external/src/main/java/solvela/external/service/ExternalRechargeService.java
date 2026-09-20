package solvela.external.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solvela.base.domain.SystemEnvironment;
import solvela.base.event.BizEventPublisher;
import solvela.enums.ExternalOrderStatusEnum;
import solvela.event.BizActionCodes;
import solvela.event.BizActionEvent;
import solvela.enums.NotificationTemplateEnum;
import solvela.external.ExternalOrder;
import solvela.external.ExternalSceneProperties;
import solvela.external.dao.ExternalOrderDao;
import solvela.external.domain.RechargeCmd;
import solvela.external.domain.RechargeReason;
import solvela.external.domain.RechargeResult;
import solvela.member.api.CouponLockCmd;
import solvela.member.api.CouponQueryApi;
import solvela.member.api.CouponTrialQuery;
import solvela.member.api.CouponTrialView;
import solvela.member.api.CouponWriteOffApi;
import solvela.member.api.CouponWriteOffCmd;
import solvela.member.api.CouponWriteOffView;
import solvela.member.service.MemberService;
import solvela.notification.domain.NotifyRequest;
import solvela.notification.service.NotificationService;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 充话费：券的<b>第一个非商城出口</b>。
 *
 * <h3>它真正验证的是什么</h3>
 * 方案 §6.2 说得很清楚：<b>充话费这个能力本身不在券方案范围内</b>
 *（要接运营商或聚合支付，是独立一块）。这里要证明的是另一件事 ——
 * 「券能被一个<b>外部场景</b>消费掉」这条路是通的，
 * 也就是 {@code scope_type = EXTERNAL} 那一档不是摆设。
 *
 * <p>所以运营商那一端是<b>假的</b>，而且必须假得<b>叫得出名字</b>：
 * 见 {@link ExternalSceneProperties.Transport}。
 *
 * <h3>三阶段核销在这里长这样</h3>
 * <pre>
 *   下单 → 试算 + 锁券 → 0-待支付
 *   支付 → 确认券        → 10-待充值
 *   执行 → 调运营商      → 20-充值中 → 30-成功 / 60-失败
 *   超时 → 释放券        → 40-已取消
 * </pre>
 *
 * <p>和商城那条路一模一样，这是刻意的：两条路的<b>券语义必须一致</b>，
 * 否则「什么时候券会回来」会变成一个要分场景记忆的事。
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalRechargeService {

    /** 单号前缀。一眼看出是外部场景单，客服不用去查表 */
    private static final String ORDER_NO_PREFIX = "E";

    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 券核销流水里的业务类型。和商城的 {@code MALL} 分开 —— 对账时要分得出两条业务线 */
    private static final String BIZ_TYPE_COUPON = "EXTERNAL";

    /** 手机号：11 位数字。校验只做形状，不做归属地那种会过期的判断 */
    private static final String PHONE_PATTERN = "^1\\d{10}$";

    private final ExternalOrderDao externalOrderDao;
    private final CouponQueryApi couponQueryApi;
    private final CouponWriteOffApi couponWriteOffApi;
    private final MemberService memberService;
    private final NotificationService notificationService;
    private final ExternalSceneProperties sceneProperties;
    private final SystemEnvironment systemEnvironment;
    /**
     * 业务动作广播。本域只说「有人在这里消费成功了」。
     *
     * <p>🔴 <b>不是</b>任务引擎。本模块排在 {@code solvela-marketing} 之后，
     * pom 里加一行就能直连它 —— 那条缝由 {@code ExternalPlayBoundaryTest} 守着，
     * 那个测试的失败信息里写着该怎么打点。
     */
    private final BizEventPublisher bizEventPublisher;

    /**
     * 🔴 生产环境不许用假充值，<b>启动即失败</b>。
     *
     * <p>它比假支付还危险一档：假支付至少只是「没收到钱」，而假充值是
     * <b>用户花了钱、券也用了，系统告诉他充值成功，但话费一分钱都没到账</b>。
     * 用户要等到查话费余额才发现，而那时候券已经核销、单据已经是成功态 ——
     * 客服查任何一张表看到的都是「一切正常」。
     */
    @PostConstruct
    void checkTransport() {
        if (sceneProperties.getTransport() == ExternalSceneProperties.Transport.DISABLED) {
            log.info("【充话费】通道 DISABLED：该场景暂未开放。这是当前生产环境的预期状态。");
            return;
        }
        if (sceneProperties.getTransport() == ExternalSceneProperties.Transport.REAL) {
            throw new IllegalStateException(
                    "solvela.external.recharge.transport=REAL，但后端【一行运营商对接代码都没有】："
                            + "配成 REAL 只会让所有充值单卡在充值中。接好运营商之后再放开这个取值，"
                            + "并把 FAKE 那条分支一起删掉。");
        }
        if (systemEnvironment.isProd()) {
            throw new IllegalStateException(
                    "solvela.external.recharge.transport=FAKE 不允许在生产环境使用："
                            + "用户花了钱、券也用了，系统告诉他充值成功，而话费一分钱都不会到账。"
                            + "这是最坏的一种错 —— 用户要等到查话费余额才发现，"
                            + "而那时候券已经核销、单据已经是成功态。");
        }
        log.warn("【充话费】当前是 FAKE 通道：下单、扣券、标成功，<但话费不会到账>。"
                        + "搜关键字【充话费-FAKE】。当前环境 {}。"
                        + "🔴 这个开关只允许在非生产环境使用，配到生产会启动失败。",
                systemEnvironment.getCurrentEnvironment());
    }

    // ------------------------------------------------------------------ 试算

    /**
     * 选券：这一笔充值能用哪些券。
     *
     * <p>和商城下单页调的是<b>同一个</b>试算接口，只是 {@code sceneCode} 换成了场景码 ——
     * 券模板里 {@code scope_type = EXTERNAL} 的那一档就是按它匹配的。
     */
    public CouponTrialView trial(Long memberId, BigDecimal amount) {
        // 充值只有现金这一侧：积分那个位置传 null，积分券会带着原因回到不可用列表里
        return couponQueryApi.trial(new CouponTrialQuery(
                memberId, null, amount, null, null, sceneProperties.getSceneCode()));
    }

    // ------------------------------------------------------------------ 下单

    /**
     * 下单：试算 → 锁券 → 落一张待支付单。
     *
     * <h3>🔴 抵扣额由服务端重新试算，不信客户端传的数</h3>
     * 和商城那条路同一条规矩。客户端只说「用哪张券」。
     *
     * <h3>⚠️ 面额只认白名单</h3>
     * 开放任意金额的话，一个「充 0.01 元」的请求会试图把一张满 100 减 10 的券
     * 套进一笔一分钱的单子里 —— 试算的门槛会拦住它，但这条路本来就不该存在，
     * 而且运营商那边也只卖固定面额。
     */
    @Transactional(rollbackFor = Exception.class)
    public RechargeResult create(RechargeCmd cmd) {
        if (sceneProperties.getTransport() == ExternalSceneProperties.Transport.DISABLED) {
            // 功能没开和功能坏了是两件事，也该是两种提示
            return RechargeResult.ofReject(RechargeReason.SCENE_NOT_AVAILABLE);
        }
        if (!StringUtils.isNotBlank(cmd.targetAccount()) || !cmd.targetAccount().matches(PHONE_PATTERN)) {
            return RechargeResult.ofReject(RechargeReason.BAD_TARGET);
        }
        BigDecimal amount = cmd.amount();
        if (amount == null || !containsFaceValue(amount)) {
            return RechargeResult.ofReject(RechargeReason.BAD_AMOUNT);
        }
        if (amount.compareTo(sceneProperties.getMinAmount()) < 0) {
            return RechargeResult.ofReject(RechargeReason.BELOW_MIN_AMOUNT);
        }

        String orderNo = generateOrderNo();
        BigDecimal discount = BigDecimal.ZERO;

        if (cmd.couponId() != null) {
            CouponTrialView trial = trial(cmd.memberId(), amount);
            CouponTrialView.Item chosen = trial.allUsable().stream()
                    .filter(item -> cmd.couponId().equals(item.couponId()))
                    .findFirst()
                    .orElse(null);
            if (chosen == null || chosen.discountAmount() == null
                    || chosen.discountAmount().signum() <= 0) {
                log.info("【充话费】券 {} 不在可用列表里，会员 {} 单号 {}",
                        cmd.couponId(), cmd.memberId(), orderNo);
                return RechargeResult.ofReject(RechargeReason.COUPON_UNUSABLE);
            }
            discount = chosen.discountAmount();

            CouponWriteOffView locked = couponWriteOffApi.lock(new CouponLockCmd(
                    cmd.couponId(), cmd.memberId(), BIZ_TYPE_COUPON, orderNo,
                    sceneProperties.getSceneCode(), amount, discount));
            if (!locked.ok()) {
                // 试算到锁定之间被另一笔抢走了。对用户就是「这张券用不了，换一张」
                log.info("【充话费】券 {} 锁不上：{}，单号 {}", cmd.couponId(), locked.message(), orderNo);
                return RechargeResult.ofReject(RechargeReason.COUPON_UNUSABLE);
            }
        }

        ExternalOrder order = new ExternalOrder();
        order.setOrderNo(orderNo);
        order.setMemberId(cmd.memberId());
        // 展示快照：记的是下单当时那个账号，改名之后不跟着变
        order.setMemberName(memberService.requireMemberName(cmd.memberId()));
        order.setSceneCode(sceneProperties.getSceneCode());
        // 明文进来，PiiTypeHandler 落库时加密 —— 与收货地址同一套密钥
        order.setTargetAccount(cmd.targetAccount());
        order.setTargetMasked(mask(cmd.targetAccount()));
        order.setOriginalAmount(amount);
        order.setCouponId(cmd.couponId());
        order.setCouponDiscount(cmd.couponId() == null ? null : discount);
        order.setPayAmount(amount.subtract(discount).max(BigDecimal.ZERO));
        order.setStatus(ExternalOrderStatusEnum.UNPAID);
        order.setExpireTime(LocalDateTime.now().plusMinutes(sceneProperties.getPayExpireMinutes()));
        externalOrderDao.insert(order);

        return RechargeResult.ofAccepted(orderNo, order.getPayAmount());
    }

    // ------------------------------------------------------------------ 支付 + 执行

    /**
     * 支付并执行。
     *
     * <h3>⚠️ 支付和执行写在一个方法里，是因为今天两者都是假的</h3>
     * 真接了支付网关和运营商之后，它们必然拆开：支付是<b>回调</b>进来的，
     * 执行是<b>异步</b>出去的，中间还要有重试和对账。
     *
     * <p>但<b>状态机现在就按拆开的样子建</b>（0 → 10 → 20 → 30/60），
     * 所以那一天到来时改的是调用顺序，不是表结构。
     *
     * <h3>🔴 {@code markExecuting} 那道闸不能省</h3>
     * 抢到它的那一个才去调运营商。没有它的话，重试和定时补偿会同时调两次 ——
     * 而那是<b>给用户充了两次话费</b>，外部接口那一侧没有回头路。
     */
    @Transactional(rollbackFor = Exception.class)
    public RechargeResult payAndExecute(String orderNo, Long memberId) {
        if (sceneProperties.getTransport() != ExternalSceneProperties.Transport.FAKE) {
            return RechargeResult.ofReject(RechargeReason.SCENE_NOT_AVAILABLE);
        }

        ExternalOrder order = externalOrderDao.getByOrderNo(orderNo);
        if (order == null || !order.getMemberId().equals(memberId)) {
            // 不存在和不是你的给同一个原因：否则这个接口能用来探测别人的单号
            return RechargeResult.ofReject(RechargeReason.ORDER_NOT_FOUND);
        }

        if (externalOrderDao.markPaid(orderNo) == 0) {
            // 抢不到闸门：超时 job 先到，或者已经付过。两种对用户是同一句话
            log.info("【充话费】{} 抢不到支付闸门", orderNo);
            return RechargeResult.ofReject(RechargeReason.ORDER_NOT_PAYABLE);
        }

        // 券在这一刻确认掉：钱算收了，没有回头路了
        confirmCoupon(order);

        if (externalOrderDao.markExecuting(orderNo) == 0) {
            // 走不到 —— 上一行刚把它改成 10，除非有并发的补偿任务。真到了就别重复调运营商
            log.warn("【充话费】{} 抢不到执行闸门，本次不调运营商", orderNo);
            return RechargeResult.ofAccepted(orderNo, order.getPayAmount());
        }

        /*
         * 🔴【充话费-FAKE】这里本该是运营商接口。
         *
         *    今天它直接返回成功 —— 话费一分钱都不会到账。
         *    这个开关配到生产会启动失败（checkTransport），因为它是这个项目里
         *    最难被发现的一种错：每一张表看起来都正常。
         */
        String externalRefNo = "FAKE-" + orderNo;
        externalOrderDao.markSuccess(orderNo, externalRefNo);
        log.warn("【充话费-FAKE】{} 标记为充值成功，外部流水 {} —— <话费不会到账>",
                orderNo, externalRefNo);

        /*
         * 打点：这一笔外部消费成功了。
         *
         * 🔴 位置在 markSuccess 【之后】而不是 markPaid 之后，这是有意的：
         *    markPaid 只说明钱收了，运营商那一步还可能失败（真接了运营商之后
         *    那是一条异步回来的结果）。在支付点打点的话，一笔充值失败的单
         *    也会给用户涨任务进度 —— 而失败的单是要退的。
         *
         * 🔴 仍然在本方法的事务内发布：AFTER_COMMIT 才投递，事务回滚时不会有事件。
         *
         * 幂等键用外部消费单号：重推安全，这是反查对账敢直接补推的前提。
         */
        bizEventPublisher.publish(new BizActionEvent(
                BizActionCodes.RECHARGE_PAID, order.getMemberId(), orderNo,
                null, LocalDateTime.now(),
                java.util.Map.of(
                        "orderNo", orderNo,
                        "sceneCode", order.getSceneCode(),
                        // 实付（抵扣之后）。「累计充值满 100 元」这类任务按它计量，
                        // 由 t_task_event.metric_source 去挑，本类不设 amount
                        "payAmount", order.getPayAmount() == null ? BigDecimal.ZERO : order.getPayAmount(),
                        "originalAmount", order.getOriginalAmount() == null ? BigDecimal.ZERO : order.getOriginalAmount())));

        notifyResult(order, true);
        return RechargeResult.ofAccepted(orderNo, order.getPayAmount());
    }

    /**
     * 取消：把券放回去。
     *
     * <p>⚠️ {@code bizRefId} 必须和锁定时<b>一样</b>（单号本身）——
     * 券那边的条件更新是 {@code WHERE locked_biz_id = ?}，
     * 传了别的就什么都不会发生，而且不报错。
     *
     * @return 是否真的取消了（false = 已被别人处理，本次什么都没做）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean cancel(ExternalOrder order, String reason) {
        if (externalOrderDao.markCancelled(order.getOrderNo(), reason) == 0) {
            // 拿到 0 行就【整单放弃补偿】，否则会把一个刚支付成功的单子的券放回去
            log.info("【充话费】{} 已被处理（多半是刚支付成功），本次跳过", order.getOrderNo());
            return false;
        }
        if (order.getCouponId() != null) {
            CouponWriteOffView released = couponWriteOffApi.release(new CouponWriteOffCmd(
                    order.getCouponId(), BIZ_TYPE_COUPON, order.getOrderNo(), reason));
            if (!released.ok()) {
                // 兜底任务会在锁定超时后接手，所以真实后果只是「晚一点回来」
                log.error("【充话费】🔴 {} 的券 {} 放不回去：{}。请人工确认用户的券回来了",
                        order.getOrderNo(), order.getCouponId(), released.message());
            }
        }
        log.info("【充话费】{} 已取消：{}", order.getOrderNo(), reason);
        return true;
    }

    private void confirmCoupon(ExternalOrder order) {
        if (order.getCouponId() == null) {
            return;
        }
        CouponWriteOffView confirmed = couponWriteOffApi.confirm(new CouponWriteOffCmd(
                order.getCouponId(), BIZ_TYPE_COUPON, order.getOrderNo(), null));
        if (!confirmed.ok()) {
            /*
             * ⚠️ 只告警不回滚：钱已经算收了、单已经在往下走。
             * 为一张券把整笔回滚，是拿一次确定的成功去换一次确定的失败。
             */
            log.error("【充话费】🔴 {} 的券 {} 确认失败：{}。"
                            + "这一单已按抵扣后的金额结算，但券没被标成已使用 —— "
                            + "用户可能把它再用一次，请人工核对",
                    order.getOrderNo(), order.getCouponId(), confirmed.message());
        }
    }

    /**
     * 告诉用户结果。
     *
     * <p>⚠️ 走的是通用的 {@code MANUAL} 模板而不是新建一个 —— 充话费只有一个场景，
     * 为它单独建模板会让通知模板表跟着场景数量增长。第二个场景进来时再抽。
     */
    private void notifyResult(ExternalOrder order, boolean success) {
        notificationService.send(NotifyRequest.of(NotificationTemplateEnum.MANUAL, order.getMemberId())
                .param("title", success ? "充值成功" : "充值失败")
                .param("content", (success ? "您为 " : "您为 ") + order.getTargetMasked()
                        + " 充值 " + order.getOriginalAmount() + " 元"
                        + (success ? "已到账。" : "未成功，我们会尽快处理。")
                        + (order.getCouponDiscount() == null
                        ? "" : "本次使用优惠券抵扣 " + order.getCouponDiscount() + " 元。"))
                .bizRefId(order.getOrderNo())
                .build());
    }

    private boolean containsFaceValue(BigDecimal amount) {
        return sceneProperties.getFaceValues().stream()
                // compareTo 而不是 equals：100 和 100.00 在 BigDecimal 里不相等，
                // 而它们对用户是同一个面额
                .anyMatch(value -> value.compareTo(amount) == 0);
    }

    /** 138****8888。只存这一份打码值，明文不存第二份 */
    private static String mask(String phone) {
        return phone.length() < 11 ? "***" : phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /** 单号：前缀 + 时间 + 4 位随机。与商城订单号同构，一眼分得出是哪条业务线 */
    private static String generateOrderNo() {
        return ORDER_NO_PREFIX + LocalDateTime.now().format(ORDER_NO_TIME)
                + String.format("%04d", RANDOM.nextInt(10000));
    }
}
