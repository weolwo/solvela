package solvela.member.entitlement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.member.GradeEntitlement;
import solvela.member.GradeEntitlementGrant;
import solvela.member.api.MemberEntitlementView;
import solvela.member.entitlement.dao.GradeEntitlementDao;
import solvela.member.entitlement.dao.GradeEntitlementGrantDao;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 我的权益列表 —— <b>只读</b>。
 *
 * <h3>⚠️ 已领取与已过期的也返回</h3>
 * 只给待领取的话，用户点完领取就什么都看不见了，会以为「刚才那个东西没了」。
 * 而「上个月我领过什么」本来就是这一页该回答的问题之一。
 *
 * <h3>排序：待领取在前，同状态按过期时间近的在前</h3>
 * 快过期的排在前面，是因为这一页存在的<b>全部理由</b>就是让人别忘了领。
 *
 * @author alaric
 * @date 2026-09-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GradeEntitlementQueryService {

    private final GradeEntitlementDao gradeEntitlementDao;
    private final GradeEntitlementGrantDao gradeEntitlementGrantDao;

    /** 一次最多给多少条。端上传什么数都不该把库拖垮 */
    private static final int MAX_LIMIT = 100;

    public List<MemberEntitlementView> mine(Long memberId, int limit) {
        if (memberId == null) {
            return List.of();
        }
        int size = Math.min(Math.max(limit, 1), MAX_LIMIT);

        List<GradeEntitlementGrant> grants = gradeEntitlementGrantDao.selectList(
                new LambdaQueryWrapper<GradeEntitlementGrant>()
                        .eq(GradeEntitlementGrant::getMemberId, memberId)
                        // 待领取(0) 在前，然后已领取(1)、已过期(2)
                        .orderByAsc(GradeEntitlementGrant::getStatus)
                        .orderByAsc(GradeEntitlementGrant::getExpireTime)
                        .last("LIMIT " + size));
        if (grants.isEmpty()) {
            return List.of();
        }

        /*
         * 权益名批量查一次，不在循环里逐个查 —— 一个人有 12 条月度券记录就是 12 次往返，
         * 而它们多半指向同一个配置。
         */
        Map<Long, GradeEntitlement> byId = gradeEntitlementDao.selectBatchIds(
                        grants.stream().map(GradeEntitlementGrant::getEntitlementId).distinct().toList())
                .stream().collect(Collectors.toMap(GradeEntitlement::getId, Function.identity()));

        return grants.stream().map(g -> {
            GradeEntitlement e = byId.get(g.getEntitlementId());
            /*
             * ⚠️ 配置被删了仍然要能显示：这条记录是用户真实拿到过的东西，
             * 藏起来等于让他的历史凭空少一条。名字退回编码快照 ——
             * 那是我们在生成时就存下来的，正是为了这一刻。
             */
            return new MemberEntitlementView(
                    g.getId(),
                    e == null ? g.getEntitlementCode() : e.getEntitlementName(),
                    e == null ? "" : e.getAssetName(),
                    g.getPeriodKey(),
                    g.getStatus(),
                    g.getExpireTime(),
                    g.getClaimTime());
        }).toList();
    }
}
