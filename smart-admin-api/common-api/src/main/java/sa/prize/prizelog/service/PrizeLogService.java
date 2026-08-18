package sa.prize.prizelog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sa.base.common.domain.PageResult;
import sa.base.common.domain.ResponseDTO;
import sa.base.common.util.SmartBeanUtil;
import sa.base.common.util.SmartPageUtil;
import sa.prize.prizelog.dao.PrizeLogDao;
import sa.prize.prizelog.domain.entity.PrizeLog;
import sa.prize.prizelog.domain.form.PrizeLogAddForm;
import sa.prize.prizelog.domain.form.PrizeLogQueryForm;
import sa.prize.prizelog.domain.vo.PrizeLogFunnelVO;
import sa.prize.prizelog.domain.vo.PrizeLogVO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 奖励记录表 Service
 *
 * @Author weolwo
 * @Date 2026-04-18 20:27:03
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class PrizeLogService {

    private final PrizeLogDao prizeLogDao;

    private static final int RATE_SCALE = 4;

    private static final long MINUTES_PER_HOUR = 60L;

    /**
     * 待审积压超过一天才提示：审批本来就不是分钟级的事，门槛太低会天天报警，报警就没人看了
     */
    private static final long MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR;

    /**
     * 分页查询
     */
    public PageResult<PrizeLogVO> queryPage(PrizeLogQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<PrizeLogVO> list = prizeLogDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 奖励漏斗。
     *
     * <p>回答翻十页记录也答不出来的问题：这段时间<b>发出去多少</b>（条数与价值是两个数，
     * 且价值必须按奖励类型分开算）、<b>有没有人挂在审批池里没人管</b>、
     * <b>有没有奖卡在半路</b>既没发出去也没标成失败。
     *
     * <p>⚠️ 措辞一律用「已发出」而不是「已发放/已到账」：营销域只知道自己把发奖指令发出去了，
     * 钱有没有真到用户手上是账务域的事。
     */
    public PrizeLogFunnelVO funnel(PrizeLogQueryForm queryForm) {
        Map<String, Object> row = prizeLogDao.selectFunnel(queryForm);
        PrizeLogFunnelVO vo = new PrizeLogFunnelVO();

        long total = toLong(row.get("totalCount"));
        long success = toLong(row.get("successCount"));
        long waiting = toLong(row.get("waitingCount"));
        long failed = toLong(row.get("failedCount"));
        long approvePending = toLong(row.get("approvePendingCount"));
        long approveRejected = toLong(row.get("approveRejectedCount"));
        long approveOldestMinutes = toLong(row.get("approveOldestMinutes"));
        long stuckWaiting = toLong(row.get("stuckWaitingCount"));

        vo.setTotalCount(total);
        vo.setMemberCount(toLong(row.get("memberCount")));
        vo.setSuccessCount(success);
        vo.setWaitingCount(waiting);
        vo.setFailedCount(failed);
        /*
         * 分母用「记录总数」而不是「已收口的」：待审批、卡在等待执行本身就是一种结果
         * （用户就是没拿到奖），把它们剔出分母会让这个比率虚高 —— 而运营要问的恰恰是
         * 「一百条发奖记录里最后有几条真发出去了」。
         */
        vo.setSuccessRate(rate(success, total));
        vo.setApproveNoneCount(toLong(row.get("approveNoneCount")));
        vo.setApprovePendingCount(approvePending);
        vo.setApprovePassedCount(toLong(row.get("approvePassedCount")));
        vo.setApproveRejectedCount(approveRejected);
        // 没有积压时 SQL 里的 MIN(...) 是 NULL，被 COALESCE 兜成 0；这里不要再解读成「等了 0 分钟」
        vo.setApproveOldestMinutes(approvePending == 0 ? 0L : approveOldestMinutes);
        vo.setStuckWaitingCount(stuckWaiting);

        // ---- 奖励类型维度：条数与价值双口径 ----
        List<PrizeLogFunnelVO.PrizeTypeStatVO> typeList = new ArrayList<>();
        for (Map<String, Object> stat : prizeLogDao.selectPrizeTypeStat(queryForm)) {
            PrizeLogFunnelVO.PrizeTypeStatVO item = new PrizeLogFunnelVO.PrizeTypeStatVO();
            long logCount = toLong(stat.get("logCount"));
            long typeSuccess = toLong(stat.get("successCount"));
            long badValue = toLong(stat.get("badValueCount"));
            item.setPrizeType(stat.get("prizeType") == null ? null : String.valueOf(stat.get("prizeType")));
            item.setLogCount(logCount);
            item.setSuccessCount(typeSuccess);
            item.setSuccessRate(rate(typeSuccess, logCount));
            item.setSuccessValue(toDecimal(stat.get("successValue")));
            item.setWaitingValue(toDecimal(stat.get("waitingValue")));
            item.setFailedValue(toDecimal(stat.get("failedValue")));
            item.setBadValueCount(badValue);
            typeList.add(item);
        }
        vo.setTypeList(typeList);

        // ---- 奖品维度分布 ----
        List<PrizeLogFunnelVO.PrizeStatVO> prizeList = new ArrayList<>();
        for (Map<String, Object> stat : prizeLogDao.selectPrizeStat(queryForm)) {
            PrizeLogFunnelVO.PrizeStatVO item = new PrizeLogFunnelVO.PrizeStatVO();
            long logCount = toLong(stat.get("logCount"));
            long prizeSuccess = toLong(stat.get("successCount"));
            item.setPrizeCode(stat.get("prizeCode") == null ? null : String.valueOf(stat.get("prizeCode")));
            item.setPrizeName(stat.get("prizeName") == null ? null : String.valueOf(stat.get("prizeName")));
            item.setPrizeType(stat.get("prizeType") == null ? null : String.valueOf(stat.get("prizeType")));
            item.setLogCount(logCount);
            item.setSuccessCount(prizeSuccess);
            item.setWaitingCount(toLong(stat.get("waitingCount")));
            item.setFailedCount(toLong(stat.get("failedCount")));
            item.setPendingCount(toLong(stat.get("pendingCount")));
            item.setSuccessRate(rate(prizeSuccess, logCount));
            prizeList.add(item);
        }
        vo.setPrizeList(prizeList);

        // ---- 失败原因分布 ----
        List<PrizeLogFunnelVO.FailReasonVO> failReasonList = new ArrayList<>();
        for (Map<String, Object> stat : prizeLogDao.selectFailReasonStat(queryForm)) {
            PrizeLogFunnelVO.FailReasonVO item = new PrizeLogFunnelVO.FailReasonVO();
            long count = toLong(stat.get("failCount"));
            item.setFailReason(stat.get("failReason") == null ? null : String.valueOf(stat.get("failReason")));
            item.setFailCount(count);
            item.setFailShare(rate(count, failed));
            failReasonList.add(item);
        }
        vo.setFailReasonList(failReasonList);

        // ---- 流程与一致性体检 ----
        List<String> issues = new ArrayList<>();
        long rejectedButSent = toLong(row.get("rejectedButSentCount"));
        if (rejectedButSent > 0) {
            issues.add("有 " + rejectedButSent + " 条记录「已驳回」却又是「已发出」：驳回的意思是这个奖不该发，"
                    + "钱却出去了。审批驳回只改 approve_status、不会回滚已经发出的派发，"
                    + "出现这种组合要么是先发后驳，要么是有人手工改过状态，必须逐条核对");
        }
        if (approvePending > 0 && approveOldestMinutes >= MINUTES_PER_DAY) {
            issues.add("待审批里最久的一条已经挂了 " + (approveOldestMinutes / MINUTES_PER_HOUR) + " 小时："
                    + "approve_mode=1 的奖唯一的出口就是有人来点「通过」，没人点它就一直停在这里，"
                    + "对用户就是「奖一直没到」");
        }
        if (stuckWaiting > 0) {
            issues.add("有 " + stuckWaiting + " 条记录不在等审批却停在「等待执行」超过 30 分钟："
                    + "这不一定是故障 —— 提案进了财务审批池时，发奖记录本就合理地停在等待执行。"
                    + "但工程里没有任何重试/补偿任务，真断链了也不会自己恢复，"
                    + "请拿这些记录的「来源单号」去提案记录页确认提案到底走到哪一步了");
        }
        long badValue = toLong(row.get("badValueCount"));
        if (badValue > 0) {
            issues.add("有 " + badValue + " 条记录的奖励体值不是数字：四个发奖策略开头都是 new BigDecimal(prizeValue)，"
                    + "解析不了就当场发奖失败；而且这批行的价值没有计入上面的「已发出价值」，"
                    + "也就是说页面上的金额是偏小的 —— 少算了多少只能逐条看，因为它们压根算不出来");
        }
        long noExternalBizNo = toLong(row.get("noExternalBizNoCount"));
        if (noExternalBizNo > 0) {
            issues.add("有 " + noExternalBizNo + " 条记录没有外部单号：跨系统防重全靠 uk_external_biz 唯一索引，"
                    + "而 MySQL 允许多个 NULL —— 这些行不受防重保护，同一笔奖重投一次就会再发一遍");
        }
        long failedNoReason = toLong(row.get("failedNoReasonCount"));
        if (failedNoReason > 0) {
            issues.add("有 " + failedNoReason + " 条记录发放失败却没有失败原因：客诉与排查时拿不出依据，"
                    + "也没法归到上面的失败原因分布里");
        }
        long successWithFailReason = toLong(row.get("successWithFailReasonCount"));
        if (successWithFailReason > 0) {
            issues.add("有 " + successWithFailReason + " 条记录「已发出」却带着失败原因：与状态自相矛盾，"
                    + "多半是失败后被人工改成了成功而没有清掉原因");
        }
        long expiredWaiting = toLong(row.get("expiredWaitingCount"));
        if (expiredWaiting > 0) {
            issues.add("有 " + expiredWaiting + " 条记录已过有效期却还停在「等待执行」：这时候再发出去也没意义了，"
                    + "需要人工确认是补发还是作废");
        }
        vo.setIssueList(issues);
        return vo;
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(PrizeLogAddForm addForm) {
        PrizeLog prizeLog = SmartBeanUtil.copy(addForm, PrizeLog.class);
        prizeLogDao.insert(prizeLog);
        return ResponseDTO.ok();
    }

    public ResponseDTO<String> updateById(PrizeLog prizeLog) {
        int updated = prizeLogDao.updateById(prizeLog);
        if (updated > 0) {
            return ResponseDTO.ok();
        }
        return ResponseDTO.userErrorParam();
    }

    public int save(PrizeLog prizeLog) {
        return prizeLogDao.insert(prizeLog);
    }

    private BigDecimal rate(long part, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(part).divide(BigDecimal.valueOf(total), RATE_SCALE, RoundingMode.HALF_UP);
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    /**
     * 金额统一保留两位。CAST 出来的 DECIMAL(18,4) 直接透出会变成「5830.0000 积分」，
     * 多出来的两位没有意义。
     */
    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal decimal = value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
        return decimal.setScale(2, RoundingMode.HALF_UP);
    }
}
