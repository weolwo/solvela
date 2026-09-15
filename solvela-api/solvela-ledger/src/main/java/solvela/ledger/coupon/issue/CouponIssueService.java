package solvela.ledger.coupon.issue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import solvela.coupon.CouponTemplate;
import solvela.enums.CouponStatusEnum;
import solvela.ledger.MemberCoupon;
import solvela.ledger.coupon.template.service.CouponTemplateService;

import java.time.LocalDateTime;

/**
 * 发券时<b>把规则快照进券行</b>的唯一地方。
 *
 * <h3>🔴 为什么是快照，不是存一个模板引用</h3>
 * 用户手里那张「满100减20」，运营把模板改成「满200减20」之后，如果核销读的是
 * 模板当前值，<b>用户手里的券就贬值了</b>。那不是显示问题，是资损与信任问题。
 *
 * <p>所以模板按 {@code (coupon_code, version)} 不可变，而发券这一刻把规则
 * <b>抄一份</b>进 {@code t_member_coupon}；核销只读会员券行，永远不碰模板。
 * 这和本项目「单据存快照」的既有约定一致（{@code t_mall_order} 存商品名、单价快照）。
 *
 * <h3>为什么本类只 build 不 insert</h3>
 * 两个调用方的<b>幂等语义不一样</b>：发奖侧靠唯一键撞 {@code DuplicateKeyException}
 * 当成功（券已经发过了，判失败会让引擎把预算退回去 —— 券发了、预算退了，
 * 两边永远对不平）；商城侧是一单 N 张的循环，靠调用方抢状态。
 * 把 insert 收进来就得把这两套语义也收进来，那会变成一个谁都看不懂的开关。
 *
 * <h3>⚠️ 刻意<b>不</b>加模板缓存</h3>
 * {@code NotificationTemplateService} 那边给「最新版」挂了 60 秒 TTL 缓存，
 * 这里没有。差别在于代价：通知模板缓存过期窗口内发出去的是<b>旧措辞</b>，
 * 而券模板缓存过期窗口内发出去的是<b>旧金额</b> —— 运营刚把「满100减20」
 * 改成「满200减20」，接下来 60 秒还在按老规则发钱。发券不在高频路径上，
 * 这一次 DB 读换的是「改完立刻生效」。
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponIssueService {

    /**
     * 没有模板时的兜底有效期。
     *
     * <p>⚠️ 这个值只在<b>降级路径</b>上用得到 —— 有模板时有效期由模板说了算。
     * 它原来在 {@code CouponAssetHandler} 和 {@code AssetGrantApiService} 里各存
     * 一份，两边的注释都挂着「等规则定下来」的 TODO。规则就是模板，两条路
     * 到这里合流了。
     */
    private static final int FALLBACK_VALID_DAYS = 30;

    /**
     * 券类型。
     *
     * <p>⚠️ 还是写死的：{@code t_coupon_template} <b>没有</b>券类型这一列，
     * {@code t_prize_config} 和 {@code t_mall_commodity} 也都没有。
     * 这一列今天没有任何代码读它，在它有语义之前，给它编一个值只会制造
     * 「看起来有意义其实没有」的数据。
     */
    private static final String DEFAULT_COUPON_TYPE = "GENERAL";

    private final CouponTemplateService couponTemplateService;

    /**
     * 拼一张待入库的券，规则来自券模板。
     *
     * <h3>券名优先级：模板名 &gt; 调用方的展示名 &gt; 券编码</h3>
     * 模板名赢，是因为<b>名字和规则必须是同一个人配的</b>。让活动侧的展示名盖过
     * 模板名，就会出现一张叫「无门槛券」而 {@code min_amount=100} 的券 ——
     * 用户看着名字去用，被拦下来，然后来找客服。
     *
     * <p>🔴 {@code couponCode / couponType / validStartTime / validEndTime} 四列
     * 在 DDL 里都是 NOT NULL 且无默认值：漏任意一个，MySQL 在严格模式下会以
     * 「Field 'xxx' doesn't have a default value」整条拒绝，而那是运行期才炸的。
     *
     * @return 填好的实体，<b>尚未入库</b>。由调用方 insert，见类注释
     */
    public MemberCoupon newCoupon(CouponIssueCmd cmd) {
        CouponTemplate template = couponTemplateService.getLatestEnabled(cmd.couponCode());

        MemberCoupon coupon = new MemberCoupon();
        coupon.setMemberId(cmd.memberId());
        coupon.setMemberName(cmd.memberName());
        coupon.setCouponCode(cmd.couponCode());
        coupon.setCouponType(DEFAULT_COUPON_TYPE);
        coupon.setSourceType(cmd.sourceType());
        coupon.setSourceBizId(cmd.sourceBizId());
        coupon.setStatus(CouponStatusEnum.UNUSED);

        LocalDateTime now = LocalDateTime.now();
        coupon.setValidStartTime(now);

        if (template == null) {
            degrade(cmd, coupon, now);
            return coupon;
        }

        coupon.setCouponName(StringUtils.isNotBlank(template.getCouponName())
                ? template.getCouponName()
                : fallbackName(cmd));

        // ---- 规则快照。核销读的就是这几列，之后再不回头看模板 ----
        coupon.setTemplateVersion(template.getVersion());
        coupon.setDiscountType(template.getDiscountType());
        coupon.setDiscountValue(template.getDiscountValue());
        coupon.setMinAmount(template.getMinAmount());
        coupon.setMaxDiscount(template.getMaxDiscount());
        coupon.setDeductTarget(template.getDeductTarget());
        coupon.setScopeType(template.getScopeType());
        coupon.setScopeRefs(template.getScopeRefs());

        coupon.setValidEndTime(resolveValidEnd(template, cmd, now));
        return coupon;
    }

    /**
     * 有效期：两种表达，模板保存时已经保证了「二选一」。
     *
     * <p>🔴 固定失效时间<b>已经过去</b>时仍然发 —— 只是喊得很大声。
     * 换成拒发的话，用户中了奖却什么都没拿到，而那是个更难解释的结果；
     * 发出去至少券包里有一张明确写着「已过期」的券，客服查得到、运营也能看到
     * 是哪个模板配错了。
     */
    private LocalDateTime resolveValidEnd(CouponTemplate template, CouponIssueCmd cmd, LocalDateTime now) {
        if (template.getValidDays() != null) {
            return now.plusDays(template.getValidDays());
        }
        LocalDateTime fixedEnd = template.getValidEndTime();
        if (fixedEnd == null) {
            // 模板保存时校验过「两种填法必须选一种」，走到这里说明是绕过接口写进去的脏数据
            log.error("【发券】券模 {} v{} 两种有效期都没填（绕过了保存校验），按兜底 {} 天发出",
                    template.getCouponCode(), template.getVersion(), FALLBACK_VALID_DAYS);
            return now.plusDays(FALLBACK_VALID_DAYS);
        }
        if (!fixedEnd.isAfter(now)) {
            log.error("【发券】🔴 券模 {} v{} 的固定失效时间 {} 已经过去了，"
                            + "这一张券发出去就是过期的。来源 {}:{}，会员 {}。"
                            + "请检查这个模板是不是该停用了",
                    template.getCouponCode(), template.getVersion(), fixedEnd,
                    cmd.sourceType(), cmd.sourceBizId(), cmd.memberId());
        }
        return fixedEnd;
    }

    /**
     * 没有模板：照发，但规则列全空。
     *
     * <h3>🔴 为什么是降级而不是拒发</h3>
     * 库里真有这种券，而且它<b>不是配错的</b>：商城在架商品「华为音乐 音乐VIP（年卡）」
     * （{@code XTYUJHUUI}）发的就是一张<b>兑换凭证</b>，不是折扣券 ——
     * 它压根没有「减多少」这回事，硬要给它配个模板才是编数据。
     *
     * <p>所以拒发会在运行期把一个在架商品变成兑换必失败，而收益只是少了一行
     * 规则为空的券。代价完全不对等。
     *
     * <p>⚠️ 但降级必须<b>看得见</b>，否则就变成这个仓库一直在骂的那种
     * 「不报错，只是没生效」。两道可见性：
     * <ul>
     *   <li>这里打 ERROR，带上券编码和来源单号；</li>
     *   <li>管理端券模板页顶部会列出「正在发但没有模板」的券编码
     *       （{@code CouponTemplateController.missing}）—— 那是给运营看的，
     *       不用等有人去翻日志。</li>
     * </ul>
     */
    private void degrade(CouponIssueCmd cmd, MemberCoupon coupon, LocalDateTime now) {
        coupon.setCouponName(fallbackName(cmd));
        coupon.setValidEndTime(now.plusDays(FALLBACK_VALID_DAYS));
        // 规则列一律留空。填 0 更糟：那会变成一张「无门槛减 0」的券，
        // 看起来配好了，实际上试算时减不出钱，而且再也分不清是没配还是配成了 0
        log.error("【发券】券模 {} 没有启用中的模板，这张券没有任何规则、核销时用不了。"
                        + "来源 {}:{}，会员 {}",
                cmd.couponCode(), cmd.sourceType(), cmd.sourceBizId(), cmd.memberId());
    }

    /** 兜底券名：调用方的展示名 &gt; 券编码。编码难看，但稳定且可追溯 */
    private static String fallbackName(CouponIssueCmd cmd) {
        return StringUtils.isNotBlank(cmd.fallbackName()) ? cmd.fallbackName() : cmd.couponCode();
    }
}
