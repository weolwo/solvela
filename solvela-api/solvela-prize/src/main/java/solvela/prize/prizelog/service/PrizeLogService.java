package solvela.prize.prizelog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.base.domain.PageResult;
import solvela.base.stat.Checkup;
import solvela.base.stat.Rate;
import solvela.base.stat.StatRow;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.dao.SolvelaPageUtil;
import solvela.prize.prizelog.dao.PrizeLogDao;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.commons.lang3.StringUtils;
import solvela.prize.PrizeLog;
import solvela.prize.prizelog.domain.command.PrizeLogAddCommand;
import solvela.prize.prizelog.domain.query.PrizeLogQuery;
import solvela.prize.prizelog.domain.dto.PrizeLogFunnelDTO;
import solvela.prize.prizelog.domain.dto.PrizeLogDTO;

import solvela.member.service.MemberService;
import solvela.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * 「最近记录」的硬上限。
     *
     * <p>🔴 {@code limit} 是调用方给的，而这条 SQL 用的是 {@code last("LIMIT " + n)} ——
     * 拼进 SQL 的数字必须自己夹住，否则一个 {@code limit=1000000} 就能把内存打爆。
     * 这里同时也是产品判断：「最近记录」超过一百条就该走分页接口了。
     */
    private static final int MAX_RECENT = 100;

    private final PrizeLogDao prizeLogDao;

    /**
     * 会员号 -> 账号：发奖流水要落展示快照
     */
    private final MemberService memberService;

    private static final long MINUTES_PER_HOUR = 60L;

    /**
     * 待审积压超过一天才提示：审批本来就不是分钟级的事，门槛太低会天天报警，报警就没人看了
     */
    private static final long MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR;

    /**
     * 分页查询
     */
    /**
     * 某个会员<b>最近</b>的奖励记录，按时间倒序。给 C 端「我的」页用。
     *
     * <p>只取 limit 条，不分页 —— 见 {@code PrizeRecordApi.listRecentRecords} 的注释。
     * 走 {@code idx_prize_log_(member_id, activity_code)} 的前缀。
     *
     * <p>⚠️ 返回<b>实体</b>，字段裁剪由调用方做。这一层不该知道哪个端要看什么。
     */
    public List<PrizeLog> listRecentByMember(Long memberId, int limit) {
        return listRecentByMember(memberId, null, limit);
    }

    /**
     * 同上，但可以只看<b>某一个活动</b>的记录。给活动专题页用。
     *
     * <p>🔴 过滤放在 SQL 里而不是查回来再筛：那个索引就是
     * {@code (member_id, activity_code)}，正好是这个查询的形状。
     * 「查最近 20 条再按活动筛」在参与多个活动的用户身上会筛出空列表 ——
     * 而他明明在这个活动里中过奖，只是那条记录排在第 21 位。
     *
     * @param activityCode 为空表示不限活动
     */
    public List<PrizeLog> listRecentByMember(Long memberId, String activityCode, int limit) {
        if (memberId == null || limit <= 0) {
            return List.of();
        }
        return prizeLogDao.selectList(new LambdaQueryWrapper<PrizeLog>()
                .eq(PrizeLog::getMemberId, memberId)
                .eq(StringUtils.isNotBlank(activityCode), PrizeLog::getActivityCode, activityCode)
                // 按 id 倒序即时间倒序（自增），比按 create_time 排少一个索引
                .orderByDesc(PrizeLog::getId)
                .last("LIMIT " + Math.min(limit, MAX_RECENT)));
    }

    public PageResult<PrizeLogDTO> queryPage(PrizeLogQuery queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<PrizeLogDTO> list = prizeLogDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
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
    public PrizeLogFunnelDTO funnel(PrizeLogQuery queryForm) {
        StatRow row = StatRow.of(prizeLogDao.selectFunnel(queryForm));

        PrizeLogFunnelDTO vo = new PrizeLogFunnelDTO();
        fillOverview(row, vo);
        vo.setTypeList(typeStats(queryForm));
        vo.setPrizeList(prizeStats(queryForm));
        vo.setFailReasonList(failReasons(queryForm, row.count("failedCount")));
        vo.setIssueList(checkup(row));
        return vo;
    }

    /**
     * 发放总量与审批池现状。
     */
    private void fillOverview(StatRow row, PrizeLogFunnelDTO vo) {
        long total = row.count("totalCount");
        long success = row.count("successCount");
        long approvePending = row.count("approvePendingCount");

        vo.setTotalCount(total);
        vo.setMemberCount(row.count("memberCount"));
        vo.setSuccessCount(success);
        vo.setWaitingCount(row.count("waitingCount"));
        vo.setFailedCount(row.count("failedCount"));
        /*
         * 分母用「记录总数」而不是「已收口的」：待审批、卡在等待执行本身就是一种结果
         * （用户就是没拿到奖），把它们剔出分母会让这个比率虚高 —— 而运营要问的恰恰是
         * 「一百条发奖记录里最后有几条真发出去了」。
         */
        vo.setSuccessRate(Rate.share(success, total));

        vo.setApproveNoneCount(row.count("approveNoneCount"));
        vo.setApprovePendingCount(approvePending);
        vo.setApprovePassedCount(row.count("approvePassedCount"));
        vo.setApproveRejectedCount(row.count("approveRejectedCount"));
        // 没有积压时 SQL 里的 MIN(...) 是 NULL，被 COALESCE 兜成 0；这里不要再解读成「等了 0 分钟」
        vo.setApproveOldestMinutes(approvePending == 0 ? 0L : row.count("approveOldestMinutes"));
        vo.setStuckWaitingCount(row.count("stuckWaitingCount"));
    }

    /**
     * 奖励类型维度：<b>条数与价值双口径</b>。
     *
     * <p>价值必须按类型分开算 —— 积分、优惠券、实物的「1000」不是同一个量纲，
     * 把它们加在一起得到的合计数没有任何意义。
     */
    private List<PrizeLogFunnelDTO.PrizeTypeStatDTO> typeStats(PrizeLogQuery queryForm) {
        List<PrizeLogFunnelDTO.PrizeTypeStatDTO> typeList = new ArrayList<>();
        for (StatRow stat : StatRow.of(prizeLogDao.selectPrizeTypeStat(queryForm))) {
            long logCount = stat.count("logCount");
            long success = stat.count("successCount");

            PrizeLogFunnelDTO.PrizeTypeStatDTO item = new PrizeLogFunnelDTO.PrizeTypeStatDTO();
            item.setPrizeType(stat.text("prizeType"));
            item.setLogCount(logCount);
            item.setSuccessCount(success);
            item.setSuccessRate(Rate.share(success, logCount));
            item.setSuccessValue(stat.amount("successValue"));
            item.setWaitingValue(stat.amount("waitingValue"));
            item.setFailedValue(stat.amount("failedValue"));
            item.setBadValueCount(stat.count("badValueCount"));
            typeList.add(item);
        }
        return typeList;
    }

    /**
     * 奖品维度分布：哪个奖发得最多、哪个奖最容易发失败。
     */
    private List<PrizeLogFunnelDTO.PrizeStatDTO> prizeStats(PrizeLogQuery queryForm) {
        List<PrizeLogFunnelDTO.PrizeStatDTO> prizeList = new ArrayList<>();
        for (StatRow stat : StatRow.of(prizeLogDao.selectPrizeStat(queryForm))) {
            long logCount = stat.count("logCount");
            long success = stat.count("successCount");

            PrizeLogFunnelDTO.PrizeStatDTO item = new PrizeLogFunnelDTO.PrizeStatDTO();
            item.setPrizeCode(stat.text("prizeCode"));
            item.setPrizeName(stat.text("prizeName"));
            item.setPrizeType(stat.text("prizeType"));
            item.setLogCount(logCount);
            item.setSuccessCount(success);
            item.setWaitingCount(stat.count("waitingCount"));
            item.setFailedCount(stat.count("failedCount"));
            item.setPendingCount(stat.count("pendingCount"));
            item.setSuccessRate(Rate.share(success, logCount));
            prizeList.add(item);
        }
        return prizeList;
    }

    /**
     * 失败原因分布。
     *
     * @param failedTotal 失败总数，用作占比分母 —— 这一列回答的是「发失败的那些里它占多少」
     */
    private List<PrizeLogFunnelDTO.FailReasonDTO> failReasons(PrizeLogQuery queryForm, long failedTotal) {
        List<PrizeLogFunnelDTO.FailReasonDTO> failReasonList = new ArrayList<>();
        for (StatRow stat : StatRow.of(prizeLogDao.selectFailReasonStat(queryForm))) {
            long count = stat.count("failCount");

            PrizeLogFunnelDTO.FailReasonDTO item = new PrizeLogFunnelDTO.FailReasonDTO();
            item.setFailReason(stat.text("failReason"));
            item.setFailCount(count);
            item.setFailShare(Rate.share(count, failedTotal));
            failReasonList.add(item);
        }
        return failReasonList;
    }

    /**
     * 流程与一致性体检。
     */
    private List<String> checkup(StatRow row) {
        long approvePending = row.count("approvePendingCount");
        long approveOldestMinutes = row.count("approveOldestMinutes");
        return new Checkup()
                .countIf(row.count("rejectedButSentCount"),
                        "有 {} 条记录「已驳回」却又是「已发出」：驳回的意思是这个奖不该发，"
                                + "钱却出去了。审批驳回只改 approve_status、不会回滚已经发出的派发，"
                                + "出现这种组合要么是先发后驳，要么是有人手工改过状态，必须逐条核对")
                .when(approvePending > 0 && approveOldestMinutes >= MINUTES_PER_DAY,
                        "待审批里最久的一条已经挂了 {} 小时：approve_mode=1 的奖唯一的出口就是有人来点「通过」，"
                                + "没人点它就一直停在这里，对用户就是「奖一直没到」",
                        approveOldestMinutes / MINUTES_PER_HOUR)
                .countIf(row.count("stuckWaitingCount"),
                        "有 {} 条记录不在等审批却停在「等待执行」超过 30 分钟："
                                + "这不一定是故障 —— 提案进了财务审批池时，发奖记录本就合理地停在等待执行。"
                                + "但工程里没有任何重试/补偿任务，真断链了也不会自己恢复，"
                                + "请拿这些记录的「来源单号」去提案记录页确认提案到底走到哪一步了")
                .countIf(row.count("badValueCount"),
                        "有 {} 条记录的奖励体值不是数字：四个发奖策略开头都是 new BigDecimal(prizeValue)，"
                                + "解析不了就当场发奖失败；而且这批行的价值没有计入上面的「已发出价值」，"
                                + "也就是说页面上的金额是偏小的 —— 少算了多少只能逐条看，因为它们压根算不出来")
                .countIf(row.count("noExternalBizNoCount"),
                        "有 {} 条记录没有外部单号：跨系统防重全靠 uk_external_biz 唯一索引，"
                                + "而 MySQL 允许多个 NULL —— 这些行不受防重保护，同一笔奖重投一次就会再发一遍")
                .countIf(row.count("failedNoReasonCount"),
                        "有 {} 条记录发放失败却没有失败原因：客诉与排查时拿不出依据，"
                                + "也没法归到上面的失败原因分布里")
                .countIf(row.count("successWithFailReasonCount"),
                        "有 {} 条记录「已发出」却带着失败原因：与状态自相矛盾，"
                                + "多半是失败后被人工改成了成功而没有清掉原因")
                .countIf(row.count("expiredWaitingCount"),
                        "有 {} 条记录已过有效期却还停在「等待执行」：这时候再发出去也没意义了，"
                                + "需要人工确认是补发还是作废")
                .issues();
    }

    /**
     * 添加
     */
    public void add(PrizeLogAddCommand addForm) {
        PrizeLog prizeLog = SolvelaBeanUtil.copy(addForm, PrizeLog.class);
        // 表单只收会员号，账号快照由服务端补 —— 顺带校验会员真实存在。
        // ⚠️ member_name 仍是 NOT NULL 且无默认值，漏了这一句整条 INSERT 会被拒。
        prizeLog.setMemberName(memberService.requireMemberName(addForm.getMemberId()));
        prizeLogDao.insert(prizeLog);
    }

    public void updateById(PrizeLog prizeLog) {
        int updated = prizeLogDao.updateById(prizeLog);
        if (updated > 0) {
            return;
        }
        throw new BusinessException();
    }

    public int save(PrizeLog prizeLog) {
        return prizeLogDao.insert(prizeLog);
    }
}
