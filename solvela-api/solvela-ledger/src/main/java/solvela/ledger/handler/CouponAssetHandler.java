package solvela.ledger.handler;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import solvela.anno.AssetStrategy;
import solvela.dispatch.DispatchOutcome;
import solvela.enums.PrizeTypeEnum;
import solvela.ledger.coupon.dao.MemberCouponDao;
import solvela.ledger.coupon.issue.CouponIssueCmd;
import solvela.ledger.coupon.issue.CouponIssueService;
import solvela.ledger.MemberCoupon;
import solvela.risk.ProposalRecord;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AssetStrategy(PrizeTypeEnum.COUPON)
public class CouponAssetHandler implements IAssetHandler {
    @Resource
    private MemberCouponDao memberCouponDao;

    /**
     * 规则与有效期都由它从券模板读出来并快照进券行。
     *
     * <p>原来这里挂着两个写死的常量（{@code GENERAL} 和 30 天）和两条
     * 「等产品规则定下来」的 TODO。规则就是券模板，2026-09-15 阶段 2 接上了。
     */
    @Resource
    private CouponIssueService couponIssueService;

    private static final String SOURCE_TYPE_PROPOSAL = "PROPOSAL";

    @Override
    public DispatchOutcome dispatch(ProposalRecord proposal) {
        // 券模直接取提案自带的 assetRef —— 由营销侧在生成提案时传入。
        // 此前是本方法反查 t_prize_log 拿 prize_code，那是「账务域依赖营销域」的错误依赖方向，
        // 拆微服务时会直接卡住；现在依赖方向翻转为「营销 -> 账务」，本域只认自己的资产引用。
        String assetRef = proposal.getAssetRef();
        if (StringUtils.isBlank(assetRef)) {
            log.error("【发券阻断】提案未指定券模 assetRef, 提案ID: {}", proposal.getId());
            return DispatchOutcome.failed("提案未指定券模，无法发券");
        }

        try {
            memberCouponDao.insert(buildCoupon(proposal, assetRef));
            log.info(">>>> [发券成功] 提案ID: {}, 券模: {}", proposal.getId(), assetRef);
            return DispatchOutcome.success();
        } catch (DuplicateKeyException e) {
            /*
             * 幂等：同一提案重复发券视为成功。判失败的话引擎会把预算还回去，
             * 而券其实已经在上一次发出去了 —— 券发了、预算退了，两边永远对不平。
             *
             * 🔴 这段 catch 在 2026-09-15 之前是【死代码】：t_member_coupon 上
             *    压根没有唯一键可违反，只有普通索引 idx_source。也就是说
             *    「重发一个提案就多一张券」这件事，不报错、不告警、没人发现。
             *
             *    补上的是 uk_source (source_type, source_biz_id)，脚本
             *    「优惠券-发券防重唯一键.sql」。真库上的守卫在
             *    CouponSourceIdempotencyLiveTest —— 那条测的不是「接住之后处理得对」
             *    （那个 mock 就能演），而是【异常真的会被抛出来】。
             */
            log.warn("【防重拦截】该提案已发过券: {}", proposal.getId());
            return DispatchOutcome.success();
        }
    }

    /**
     * 拼一张券 —— 规则、券名、有效期<b>全部来自券模板</b>（{@link CouponIssueService}）。
     *
     * <h3>提案的 assetName 现在只是<b>兜底</b>了</h3>
     * 模板存在时用模板名，因为<b>名字和规则必须是同一个人配的</b> ——
     * 让活动侧的展示名盖过模板名，就会出现一张叫「无门槛券」而
     * {@code min_amount=100} 的券，用户看着名字去用，被拦下来，然后来找客服。
     *
     * <p>⚠️ 但 assetName 这条通路<b>仍然要传</b>：没有模板时它就是券名。
     * 而且它身上还背着一个线上事故 —— 这里原来写的是
     * {@code proposal.getRemark()}，而 remark 在
     * {@code ProposalRecordService.saveProposal} 里被固定写成「提案生成成功」，
     * 于是发出去的券全都叫「提案生成成功」（306 张，2026-09-15 阶段 0 清掉了）。
     * 根因不是随手写错：依赖方向从「账务→营销」翻转之后，名称这条信息没有了
     * 搬运通道，remark 是当时唯一够得着的字段。正解是让提案携带展示名，
     * 而不是让账务域回头去查营销域的表 —— v3.45.0 已经这么改了。
     *
     * <p>🔴 <b>不要</b>把兜底改回 remark：remark 会被执行引擎改写成失败原因，
     * 那会让重试后发出的券叫「预算已耗尽」。券编码难看，但稳定且可追溯。
     */
    private MemberCoupon buildCoupon(ProposalRecord proposal, String assetRef) {
        return couponIssueService.newCoupon(new CouponIssueCmd(
                assetRef,
                proposal.getMemberId(),
                // 展示快照直接沿用提案上的那一份，不再查会员表：
                // 提案落库时已经把「当时那个账号」记下来了
                proposal.getMemberName(),
                SOURCE_TYPE_PROPOSAL,
                /*
                 * 溯源提案ID：客服拿着一张券要回答「这是哪次活动发的」，靠的就是这一列。
                 * 它同时是 uk_source 的幂等键。
                 *
                 * ⚠️ 这是唯一一个【没有序号】的来源（人工发券是「工单号:会员号:序号」，
                 *    商城是「订单号:序号」）。今天成立，因为一个提案确实只发一张券。
                 *    哪天要「一个提案发 N 张」，必须先给这里加序号 ——
                 *    否则第二张会撞唯一键，而 catch 会把它当成重复提交默默吞掉，
                 *    表现是「说好发 3 张，用户只收到 1 张」。
                 */
                proposal.getId().toString(),
                proposal.getAssetName()));
    }
}
