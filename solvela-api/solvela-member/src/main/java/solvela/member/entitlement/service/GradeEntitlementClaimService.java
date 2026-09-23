package solvela.member.entitlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.exception.BusinessException;
import solvela.member.GradeEntitlement;
import solvela.member.GradeEntitlementGrant;
import solvela.member.api.AssetGrantApi;
import solvela.member.api.AssetGrantCmd;
import solvela.member.api.AssetGrantResult;
import solvela.member.entitlement.EntitlementGrantStatus;
import solvela.member.entitlement.dao.GradeEntitlementDao;
import solvela.member.entitlement.dao.GradeEntitlementGrantDao;

import java.time.LocalDateTime;

/**
 * 领取一份待领取的权益。
 *
 * <h3>🔴 先抢占，再发放 —— 顺序不能反</h3>
 * 抢占用的是条件 UPDATE（{@code WHERE id=? AND member_id=? AND status=0 AND expire_time>now}），
 * 影响 0 行就是没抢到。<b>先发放再改状态</b>的话，两个请求同时点会双双通过发放，
 * 然后其中一个改状态失败 —— 而那时东西已经发出去两份了。
 *
 * <h3>⚠️ 抢占成功之后发放失败，状态<b>不回滚</b></h3>
 * 这是刻意的，理由是<b>两种错的代价不对称</b>：
 * <ul>
 *   <li>回滚成待领取 → 用户再点一次 → 资产侧唯一键挡住重复 → <b>他永远领不到，
 *       而且每次都以为自己该能领</b>；</li>
 *   <li>不回滚 → 记录停在「已领取」但 {@code grant_result} 写着失败原因 →
 *       运营查得到、能补发，用户只吃一次亏。</li>
 * </ul>
 * 而真正兜住「不会重复发」的，是资产侧那几个唯一键认的
 * {@link GradeEntitlementGrant#getGrantBizId()} —— 它在<b>生成时</b>就定死了，
 * 重试多少次都是同一个号。
 *
 * <h3>为什么领取时不再判等级</h3>
 * 等级是<b>生成时</b>的快照。月中降级不该把已经给出去的收回；
 * 而更要紧的是反方向：<b>让用户看见「可领取」却领不了，比一开始就不给更伤</b>。
 *
 * @author alaric
 * @date 2026-09-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GradeEntitlementClaimService {

    private final GradeEntitlementDao gradeEntitlementDao;
    private final GradeEntitlementGrantDao gradeEntitlementGrantDao;
    private final AssetGrantApi assetGrantApi;

    /**
     * 领取。
     *
     * @param memberId 会员号。<b>必须由登录态给</b>，不能信客户端传的 ——
     *                 条件更新里带它是越权防线，不是冗余条件
     * @param grantId  待领取记录 id
     * @return 领到的东西叫什么，给端上做提示
     * @throws BusinessException 领不了（已领过、过期了、不是他的）
     */
    public String claim(Long memberId, Long grantId) {
        GradeEntitlementGrant grant = gradeEntitlementGrantDao.selectById(grantId);
        if (grant == null || !grant.getMemberId().equals(memberId)) {
            /*
             * 「不存在」与「不是你的」合并成同一句话：分开说等于告诉调用方
             * 「这个 id 是存在的，只是不属于你」—— 那是一条可以用来枚举的信息。
             */
            throw new BusinessException("这份权益不存在");
        }

        LocalDateTime now = LocalDateTime.now();

        // 🔴 先抢占。影响 0 行 = 没抢到（已领过 / 过期了 / 并发下被另一个请求拿走了）
        int seized = gradeEntitlementGrantDao.tryClaim(grantId, memberId, now);
        if (seized == 0) {
            throw new BusinessException(describeWhyNotClaimable(grant, now));
        }

        GradeEntitlement entitlement = gradeEntitlementDao.selectById(grant.getEntitlementId());
        if (entitlement == null) {
            /*
             * 配置被删了而记录还在。抢占已经成功，不回滚 —— 理由见类注释。
             * 这条 ERROR 必须有：它指向一个「运营删了正在用的配置」的操作，
             * 而受影响的是所有还没领的人，不止这一个。
             */
            log.error("【权益领取】配置已不存在，发不出去。grantId={}, entitlementId={}, memberId={}",
                    grantId, grant.getEntitlementId(), memberId);
            gradeEntitlementGrantDao.markResult(grantId, "权益配置已删除，无法发放");
            throw new BusinessException("这份权益已下架，请联系客服");
        }

        return dispatch(grant, entitlement, memberId);
    }

    private String dispatch(GradeEntitlementGrant grant, GradeEntitlement entitlement, Long memberId) {
        AssetGrantCmd cmd = new AssetGrantCmd(
                memberId,
                entitlement.getAssetType(),
                entitlement.getAssetRef(),
                entitlement.getAssetName(),
                entitlement.getQuantity(),
                entitlement.getAmount(),
                // 来源类型让资产侧的流水看得出这笔是从哪来的 —— 与商城的 MALL 并列
                "GRADE_ENTITLEMENT",
                /*
                 * 🔴 单号用生成时就定死的那个，不是现生成。
                 * 资产侧的防重唯一键认的正是它：重试多少次都是同一个号，
                 * 所以「抢占成功但发放超时」再点一次也不会多发。
                 */
                grant.getGrantBizId(),
                "GRADE_ENTITLEMENT",
                null, null, null,
                entitlement.getEntitlementName());

        try {
            AssetGrantResult result = assetGrantApi.grant(cmd);
            if (result != null && result.accepted()) {
                gradeEntitlementGrantDao.markResult(grant.getId(), "已发放");
                log.info("【权益领取】成功。memberId={}, entitlement={}, bizId={}",
                        memberId, entitlement.getEntitlementCode(), grant.getGrantBizId());
                return entitlement.getAssetName();
            }
            String reason = result == null ? "发放服务无响应" : String.valueOf(result.reason());
            gradeEntitlementGrantDao.markResult(grant.getId(), "发放被拒：" + reason);
            log.error("【权益领取】发放被拒。memberId={}, entitlement={}, bizId={}, reason={}",
                    memberId, entitlement.getEntitlementCode(), grant.getGrantBizId(), reason);
            throw new BusinessException("领取失败，请联系客服");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            /*
             * 发放这一步挂了（网络、下游不可用）。状态停在「已领取」不回滚 ——
             * 理由见类注释：回滚会让用户陷入「看得见、点得动、永远领不到」。
             * 单号已经定死，运营补发走同一个号，资产侧不会重复。
             */
            gradeEntitlementGrantDao.markResult(grant.getId(), "发放异常：" + e.getClass().getSimpleName());
            log.error("【权益领取】发放异常。memberId={}, entitlement={}, bizId={}",
                    memberId, entitlement.getEntitlementCode(), grant.getGrantBizId(), e);
            throw new BusinessException("领取失败，请稍后重试或联系客服");
        }
    }

    /**
     * 没抢到的时候，说清楚是为什么。
     *
     * <p>⚠️ 这里读的是<b>抢占之前</b>那一份快照，所以「已领取」有可能是
     * 并发下另一个请求刚刚领走的。两种情况对用户是同一句话，不必区分。
     */
    private String describeWhyNotClaimable(GradeEntitlementGrant grant, LocalDateTime now) {
        if (grant.getStatus() != null && grant.getStatus() == EntitlementGrantStatus.CLAIMED) {
            return "这份权益已经领过了";
        }
        if (grant.getStatus() != null && grant.getStatus() == EntitlementGrantStatus.EXPIRED) {
            return "这份权益已过期";
        }
        if (grant.getExpireTime() != null && !grant.getExpireTime().isAfter(now)) {
            // 状态还是待领取但时间已过：job 还没来得及置过期，对用户是同一件事
            return "这份权益已过期";
        }
        return "这份权益已经领过了";
    }
}
