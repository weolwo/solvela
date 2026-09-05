package solvela.ledger.grant;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import solvela.code.BizErrorCode;
import solvela.enums.CouponStatusEnum;
import solvela.enums.DeliveryStatusEnum;
import solvela.enums.PrizeTypeEnum;
import solvela.exception.BusinessException;
import solvela.ledger.MemberCoupon;
import solvela.ledger.PhysicalDelivery;
import solvela.ledger.coupon.dao.MemberCouponDao;
import solvela.ledger.logistic.dao.PhysicalDeliveryDao;
import solvela.ledger.wallet.service.MemberWalletService;
import solvela.member.api.AssetGrantApi;
import solvela.member.api.AssetGrantCmd;
import solvela.member.api.AssetGrantReason;
import solvela.member.api.AssetGrantResult;
import solvela.member.service.MemberService;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * {@link AssetGrantApi} 的实现：把「把东西真正发给用户」这件事暴露给服务端内部的调用方。
 *
 * <h3>它和 {@code IAssetHandler} 是同一批表的两个入口</h3>
 * 那套的入参是 {@code ProposalRecord}，服务的是<b>发奖</b>（有审批、有预算、有风控）。
 * 本类服务的是<b>兑换</b>（用户花自己的积分，mall.sql 明写不走提案）。
 * 为了复用而硬造一条假提案，还要连带硬编一条假的优惠配置，
 * 那是拿数据的正确性换代码行数 —— 所以刻意是两个入口。
 *
 * <p>但<b>落的是同一批表、同样的列</b>：{@code t_physical_delivery} /
 * {@code t_member_coupon} / {@code t_member_wallet}，只是 {@code source_type} 不同。
 * 运营的发货台、物流导入、券管理一行都不用改，DDL 里
 * 「商城以 source_type='MALL' 写入即可」说的就是这条。
 *
 * <h3>🔴 事务边界在调用方</h3>
 * 和 {@code AssetDebitApiService} 同一条规矩：本类<b>没有</b> {@code @Transactional}，
 * 它跑在调用方的事务里，这样「发了货但单子没标完成」不会发生。
 *
 * <h3>返回值 vs 异常，是给调用方看的信号</h3>
 * <b>返回拒绝 = 别再试了</b>（商品没配券模、地址被删、类型不认识）——
 * 调用方该把单子标成「履约失败」等人来看。
 * <b>抛异常 = 可以再试</b>（数据库抖动）—— 调用方该回滚，让下一轮重试接手。
 * 把这两类混成一种，结果要么是永远重试一个永远不会成功的单子，
 * 要么是一次网络抖动让用户的东西彻底发不出来。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetGrantApiService implements AssetGrantApi {

    private final PhysicalDeliveryDao physicalDeliveryDao;
    private final MemberCouponDao memberCouponDao;
    private final MemberWalletService memberWalletService;
    private final MemberService memberService;

    /**
     * 券有效期默认天数。
     *
     * <p>与 {@code CouponAssetHandler} 里那个常量取值一致但<b>刻意各存一份</b>：
     * 那边的注释挂着「等确定是按固定天数还是按活动结束时间」的 TODO ——
     * 商城的券没有活动可依，规则定下来的那天两边会分道扬镳。
     * 现在合并等于将来要拆，而拆的时候没人记得它们本来就不是一回事。
     */
    private static final int COUPON_VALID_DAYS = 30;

    /** 券类型。t_prize_config 没有券类型字段，商城这边同样没有，先给通用值 */
    private static final String DEFAULT_COUPON_TYPE = "GENERAL";

    @Override
    public AssetGrantResult grant(AssetGrantCmd cmd) {
        PrizeTypeEnum assetType = resolveAssetType(cmd.assetType());
        if (assetType == null) {
            log.error("【资产发放】未知的资产类型 {}，bizRefId={} —— 调用方传了个域里没有的值",
                    cmd.assetType(), cmd.bizRefId());
            return AssetGrantResult.ofReject(AssetGrantReason.UNSUPPORTED_ASSET_TYPE);
        }
        try {
            return switch (assetType) {
                case PHYSICAL -> grantPhysical(cmd);
                case COUPON -> grantCoupon(cmd);
                case BALANCE, SCORE -> grantWallet(cmd, assetType);
                /*
                 * MARKER 不动账（纯占位奖项），LOTTERY / CUSTOM 的派发策略至今没实现。
                 * 走到这里说明上游把一个「发不出去」的类型当成商品类型配了 ——
                 * 不兜底、不假装成功：假装成功的表现是用户订单显示已完成而手里什么都没有。
                 */
                case MARKER, LOTTERY, CUSTOM -> {
                    log.error("【资产发放】{} 没有发放通道，bizRefId={}", assetType, cmd.bizRefId());
                    yield AssetGrantResult.ofReject(AssetGrantReason.UNSUPPORTED_ASSET_TYPE);
                }
            };
        } catch (BusinessException e) {
            /*
             * 乐观锁冲突是<b>可重试</b>的，所以它不翻成返回值，继续上抛让整个履约事务回滚 ——
             * 单子退回「待履约」，等下一轮。翻成拒绝的话单子会被标成「履约失败」，
             * 而它其实只是撞上了一次并发。
             */
            if (BizErrorCode.ACCOUNT_BALANCE_CHANGED.equals(e.getErrorCode())) {
                throw e;
            }
            AssetGrantReason reason = translate(e);
            if (reason == AssetGrantReason.UNKNOWN) {
                log.warn("【资产发放】未归类的拒绝原因，bizRefId={} msg={}", cmd.bizRefId(), e.getMessage(), e);
            }
            return AssetGrantResult.ofReject(reason);
        }
    }

    /* ---------------- 实物：生成待发货单，不负责发货 ---------------- */

    /**
     * 实物是三段式履约：① 建履约单 ② 运营发货 ③ 回填物流单号。
     * 所以「发放成功」对实物的含义是<b>履约单已建立</b>，不是东西已寄出。
     *
     * <p>与中奖那条链路的差别只有一处：<b>收件三要素在这里是已知的</b> ——
     * 商城下单时用户就选好了地址，而中奖时还不知道寄到哪
     *（那三列因此是可空的，见 {@code PhysicalAssetHandler}）。
     */
    private AssetGrantResult grantPhysical(AssetGrantCmd cmd) {
        if (StringUtils.isAnyBlank(cmd.receiverName(), cmd.receiverPhone(), cmd.receiverAddress())) {
            // 重试也不会让地址长出来 —— 这是拒绝，不是故障
            log.error("【实物发放】收件信息不全，bizRefId={}", cmd.bizRefId());
            return AssetGrantResult.ofReject(AssetGrantReason.RECEIVER_REQUIRED);
        }

        PhysicalDelivery delivery = new PhysicalDelivery();
        delivery.setMemberId(cmd.memberId());
        delivery.setMemberName(memberService.requireMemberName(cmd.memberId()));
        delivery.setSourceType(cmd.sourceType());
        delivery.setSourceBizId(cmd.bizRefId());
        // 明文进来，PiiTypeHandler 落库时加密 —— 与中奖补填地址那条路同一套密钥
        delivery.setReceiverName(cmd.receiverName());
        delivery.setReceiverPhone(cmd.receiverPhone());
        delivery.setReceiverAddress(cmd.receiverAddress());
        delivery.setStatus(DeliveryStatusEnum.PENDING);

        try {
            physicalDeliveryDao.insert(delivery);
            log.info(">>>> [实物履约单已建立] {}:{}, 待运营发货", cmd.sourceType(), cmd.bizRefId());
            return AssetGrantResult.ofAccepted(String.valueOf(delivery.getId()));
        } catch (DuplicateKeyException e) {
            /*
             * uk_t_biz_phy_dlv_src(source_biz_id, source_type) 挡住的重复投递。
             * 这是幂等成功，不是失败 —— 单子已经在了，运营照样发得出去。
             * 回原来那条的 id，否则订单上的 fulfill_ref_id 会是空的。
             */
            Long existingId = physicalDeliveryDao.selectOne(Wrappers.<PhysicalDelivery>lambdaQuery()
                            .eq(PhysicalDelivery::getSourceBizId, cmd.bizRefId())
                            .eq(PhysicalDelivery::getSourceType, cmd.sourceType()))
                    .getId();
            log.warn("【防重拦截】{}:{} 已生成过履约单 {}", cmd.sourceType(), cmd.bizRefId(), existingId);
            return AssetGrantResult.ofAccepted(String.valueOf(existingId));
        }
    }

    /* ---------------- 券：一份一行 ---------------- */

    /**
     * 发券。<b>quantity 份就是 quantity 行</b> —— 券是实例类资产，
     * 不能像余额那样把份数乘进金额里。
     *
     * <h3>🔴 source_biz_id 带序号后缀</h3>
     * 一单兑 3 张券会产生 3 行，它们的来源单号相同。加 {@code :1 / :2 / :3}
     * 后缀让每一行有唯一的来源标识 —— 这样 {@code t_member_coupon} 将来补上
     * {@code UNIQUE(source_type, source_biz_id)} 时，这条路是走得通的
     *（今天那张表<b>只有普通索引</b>，所以幂等靠调用方抢状态，见 {@link AssetGrantApi#grant}）。
     *
     * <p>后缀格式与奖品链路的 {@code external_biz_no} 一致（{@code 单号:序号}），
     * 都可以用 {@code LIKE '单号:%'} 反查回来。
     */
    private AssetGrantResult grantCoupon(AssetGrantCmd cmd) {
        String assetRef = cmd.assetRef();
        if (StringUtils.isBlank(assetRef)) {
            // 商品配置漏了券模编码。重试一万次也发不出来
            log.error("【发券阻断】未指定券模 assetRef，bizRefId={}", cmd.bizRefId());
            return AssetGrantResult.ofReject(AssetGrantReason.ASSET_REF_REQUIRED);
        }

        String memberName = memberService.requireMemberName(cmd.memberId());
        LocalDateTime now = LocalDateTime.now();
        int quantity = cmd.quantityOrOne();
        Long firstId = null;

        for (int seq = 1; seq <= quantity; seq++) {
            MemberCoupon coupon = new MemberCoupon();
            coupon.setMemberId(cmd.memberId());
            coupon.setMemberName(memberName);
            coupon.setCouponCode(assetRef);
            coupon.setCouponType(DEFAULT_COUPON_TYPE);
            /*
             * 券名会直接显示给用户。取不到就回退用编码 ——
             * 编码难看但稳定可追溯，而拿备注顶替会让用户收到一张叫「提案生成成功」的券
             *（CouponAssetHandler 里那段红字记的就是这个线上事故）。
             */
            coupon.setCouponName(StringUtils.isNotBlank(cmd.assetName()) ? cmd.assetName() : assetRef);
            coupon.setValidStartTime(now);
            coupon.setValidEndTime(now.plusDays(COUPON_VALID_DAYS));
            coupon.setSourceType(cmd.sourceType());
            coupon.setSourceBizId(cmd.bizRefId() + ":" + seq);
            coupon.setStatus(CouponStatusEnum.UNUSED);

            memberCouponDao.insert(coupon);
            if (firstId == null) {
                firstId = coupon.getId();
            }
        }
        log.info(">>>> [发券成功] {}:{}, 券模 {} × {}", cmd.sourceType(), cmd.bizRefId(), assetRef, quantity);
        return AssetGrantResult.ofAccepted(String.valueOf(firstId));
    }

    /* ---------------- 现金/积分：钱包入账 ---------------- */

    /**
     * 值类资产直接入账。<b>实发 = 单份面额 × 份数</b>。
     *
     * <p>复用 {@code executeWalletRefund}（「通用退还/入账」）而不是
     * {@code executeWalletCharge}：后者的入参是 {@code ProposalRecord}，
     * 而商城没有提案。两者的动作是同一个 —— 加余额 + 写一条 INCOME 流水。
     *
     * <p>幂等在这条路上是白给的：{@code t_member_asset_transaction} 有
     * {@code UNIQUE(biz_ref_id, asset_type)}，重复入账会撞唯一键。
     * 注意它和商城扣积分用的是<b>同一个 bizRefId（订单号）</b>但不同 asset_type
     *（扣的是 SCORE，发的是 BALANCE），所以不会互相挡住。
     */
    private AssetGrantResult grantWallet(AssetGrantCmd cmd, PrizeTypeEnum assetType) {
        BigDecimal unit = cmd.amount();
        if (unit == null || unit.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("【钱包发放】面额不是正数，bizRefId={} amount={}", cmd.bizRefId(), unit);
            return AssetGrantResult.ofReject(AssetGrantReason.AMOUNT_INVALID);
        }
        BigDecimal total = unit.multiply(BigDecimal.valueOf(cmd.quantityOrOne()));
        memberWalletService.executeWalletRefund(cmd.memberId(), assetType, total,
                cmd.bizType(), cmd.bizRefId(), cmd.remark());
        log.info(">>>> [钱包入账] {}:{}, {} {}", cmd.sourceType(), cmd.bizRefId(), assetType, total);
        // 流水 id 拿不到（executeWalletRefund 返回 void），用单号本身作引用：它就是那条流水的 biz_ref_id
        return AssetGrantResult.ofAccepted(cmd.bizRefId());
    }

    /* ---------------- 翻译 ---------------- */

    /**
     * 业务异常 → 拒绝原因。<b>只翻「重试也没用」的那几种</b>，
     * 可重试的那些在调用处就上抛了，走不到这里。
     *
     * <p>⚠️ 这里按 message 判是<b>将就</b>，不是范本：{@code MemberWalletService}
     * 对「钱包冻结」和「会员不存在」抛的是不带专属 ErrorCode 的通用异常。
     * 与 {@code AssetDebitApiService} 里那段同病 —— 那边的红字写明了
     * 「按 ErrorCode 判而不是按 message 判」，两处都等同一件事：
     * 给这两种情况补上专属 ErrorCode，然后把这个方法改成按码判。
     */
    private static AssetGrantReason translate(BusinessException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("冻结")) {
            return AssetGrantReason.WALLET_UNAVAILABLE;
        }
        if (message.contains("会员不存在")) {
            return AssetGrantReason.MEMBER_NOT_FOUND;
        }
        return AssetGrantReason.UNKNOWN;
    }

    /** 认不出的资产类型返回 null 而不是抛 —— 那是调用方的 bug，但不该表现成 500 */
    private static PrizeTypeEnum resolveAssetType(String value) {
        if (value == null) {
            return null;
        }
        for (PrizeTypeEnum type : PrizeTypeEnum.values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        return null;
    }
}
