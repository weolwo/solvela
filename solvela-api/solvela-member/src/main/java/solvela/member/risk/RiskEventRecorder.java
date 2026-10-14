package solvela.member.risk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.member.MemberRiskEvent;
import solvela.member.MemberRiskHit;
import solvela.member.risk.dao.MemberRiskEventDao;
import solvela.member.risk.dao.MemberRiskHitDao;

import java.util.List;

/**
 * 把一次尝试的决策流水与命中明细写进库。<b>只有这一处写这两张表。</b>
 *
 * <h3>🔴 写失败绝不影响登录</h3>
 * 与 {@code MemberAuthService.saveLoginLog} 同一条纪律：风控流水的价值再高也高不过登录本身。
 * 所以这里<b>吞掉所有异常</b>，只打 ERROR。
 *
 * <h3>不开事务</h3>
 * 流水先写、命中后写，两者不在一个事务里 —— 命中写失败时留下一行没有明细的流水，
 * 那仍然是有用的（结论、结局、矩阵坐标都在流水上，§5.3）。
 * 为了这点一致性把登录接口拖进一个事务里不值得，而且这两张表是分区的 append-only 表，
 * 没有任何人会读到「写了一半」的中间状态。
 *
 * <h3>同步写，不进线程池</h3>
 * 理由见方案 §3.9：被攻击时登录量暴涨，内存队列满了丢掉的<b>正是那次攻击的流水</b>；
 * 而库慢的时候异步也救不了这次登录 —— 它本来就要在同一个库上查会员、写登录日志。
 *
 * @Date 2026-10-15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskEventRecorder {

    private final MemberRiskEventDao eventDao;

    private final MemberRiskHitDao hitDao;

    /**
     * 写一行流水 + 它的命中明细。
     *
     * @return 流水的 id；写失败返回 null（调用方据此知道凭票里没法带事件引用）
     */
    public Long record(MemberRiskEvent event, List<MemberRiskHit> hits) {
        try {
            eventDao.insertEvent(event);
        } catch (Exception e) {
            log.error("【风控】决策流水写入失败, traceId: {}, decision: {}", event.getTraceId(), event.getDecision(), e);
            return null;
        }
        Long eventId = event.getId();
        if (hits.isEmpty() || eventId == null) {
            return eventId;
        }
        try {
            for (MemberRiskHit hit : hits) {
                hit.setEventId(eventId);
                // 🔴 必须与流水同一个时间：两表按 (id, create_time) 关联才能裁剪分区
                hit.setCreateTime(event.getCreateTime());
            }
            hitDao.insertHits(hits);
        } catch (Exception e) {
            log.error("【风控】命中明细写入失败（流水已写入, eventId: {}）, 命中 {} 条", eventId, hits.size(), e);
        }
        return eventId;
    }
}
