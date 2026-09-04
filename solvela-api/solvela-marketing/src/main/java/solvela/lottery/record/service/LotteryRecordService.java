package solvela.lottery.record.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import solvela.base.domain.PageResult;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.stat.Checkup;
import solvela.base.stat.Rate;
import solvela.base.stat.StatRow;
import solvela.lottery.constant.LotteryConst;
import solvela.lottery.record.dao.LotteryRecordDao;
import solvela.lottery.record.domain.query.LotteryRecordQuery;
import solvela.lottery.record.domain.dto.LotteryRecordFunnelDTO;
import solvela.lottery.record.domain.dto.LotteryRecordDTO;
import solvela.prize.PrizeConfig;
import solvela.prize.prizeconfig.service.PrizeCatalog;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用户号码记录 Service —— <b>只读</b>。
 *
 * <h3>⚠️ add / update 已刻意移除</h3>
 * 这张表存的是<b>用户手里的号码本身</b>，比抽奖流水更不能碰：
 * <ul>
 *   <li>{@code security_sign} 是防篡改签名，用户凭它自证「这个号码确实是系统发给我的」。
 *       后台能改签名，整套自证机制就是摆设；</li>
 *   <li>{@code win_status} / {@code prize_level} / {@code prize_code} 是派奖依据，
 *       改一行就等于凭空造一个中奖者，或抹掉一个真中奖者；</li>
 *   <li>{@code ticket_number} 与 {@code sequence_no} 是 FPE 双射的两端，
 *       改任一个都会让号码反解验真失败。</li>
 * </ul>
 * 而且这两个接口从来没有正当用途：记录由 {@code TicketPersistService} 在领号链路里写入、
 * 由开奖核销 SQL 批量更新中奖状态，没有任何场景需要人工补录或修改一张号码。
 *
 * <p>本 Service 现在只服务于「查」——分页明细 + 漏斗分析。
 *
 * @Author weolwo
 * @Date 2026-05-09
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class LotteryRecordService {

    private final LotteryRecordDao lotteryRecordDao;
    private final PrizeCatalog prizeCatalog;

    /**
     * 分页查询
     */
    public PageResult<LotteryRecordDTO> queryPage(LotteryRecordQuery queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<LotteryRecordDTO> list = lotteryRecordDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }

    /**
     * 购彩记录漏斗。
     *
     * <p>回答的是翻十页号码也答不出来的问题：中奖率多少、各奖级各中了几注、
     * <b>中了奖的到底发出去没有</b>。最后一个是本页独有的价值 ——
     * 中奖只是第一步，奖品要经派发链路真正到用户手上才算完。
     */
    public LotteryRecordFunnelDTO funnel(LotteryRecordQuery queryForm) {
        StatRow row = StatRow.of(lotteryRecordDao.selectFunnel(queryForm));

        LotteryRecordFunnelDTO vo = new LotteryRecordFunnelDTO();
        fillOverview(row, vo);
        vo.setIssueList(checkup(row));
        vo.setPrizeLevelList(prizeLevels(queryForm, row.count("winCount")));
        return vo;
    }

    /**
     * 号码总量、开奖结果分布，以及中奖之后的派发进度。
     */
    private void fillOverview(StatRow row, LotteryRecordFunnelDTO vo) {
        long total = row.count("totalCount");
        long lose = row.count("loseCount");
        long win = row.count("winCount");
        long members = row.count("memberCount");

        vo.setTotalCount(total);
        vo.setWaitCount(row.count("waitCount"));
        vo.setLoseCount(lose);
        vo.setWinCount(win);
        vo.setMemberCount(members);
        /*
         * 中奖率的分母用「已开奖」而不是「全部」：未开奖的号码还没揭晓，
         * 把它们算进分母会让活动刚开始时的中奖率无限接近 0，那个数字没有意义。
         */
        vo.setWinRate(Rate.share(win, lose + win));
        vo.setTicketPerMember(Rate.average(total, members));

        vo.setDispatchWaitCount(row.count("dispatchWaitCount"));
        vo.setDispatchedCount(row.count("dispatchedCount"));
        vo.setDispatchFailedCount(row.count("dispatchFailedCount"));
    }

    /**
     * 一致性体检：专门用来发现被手工改坏或链路出错的记录。
     */
    private List<String> checkup(StatRow row) {
        return new Checkup()
                .countIf(row.count("dispatchFailedCount"),
                        "有 {} 张号码已中奖但派发失败：用户看到自己中了奖、系统也认，但奖品没发出去。"
                                + "请到「派发记录」查看失败原因并重新触发派奖")
                .countIf(row.count("winButNoPrize"),
                        "有 {} 张号码已中奖但没有奖品编码：派奖时不知道该发什么，"
                                + "通常是核销时奖级规则里的 prize_code 为空")
                .countIf(row.count("winButLevelNone"),
                        "有 {} 张号码标记为已中奖，奖级却是 {}（未中奖占位）："
                                + "用户端按奖级排序会把它们沉到最底，看起来像没中奖",
                        LotteryConst.PRIZE_LEVEL_NONE)
                .countIf(row.count("loseButHasLevel"),
                        "有 {} 张号码未中奖却带着有效奖级：与中奖状态自相矛盾，"
                                + "多半是历史数据或人工改动留下的")
                .countIf(row.count("noSign"),
                        "有 {} 张号码的防篡改签名为空：这些号码无法自证真伪，"
                                + "用户申诉时拿不出凭据")
                .issues();
    }

    /**
     * 奖级分布：各奖级各中了几注、发出去多少价值。
     *
     * @param winTotal 中奖总数，用作占比分母 —— 这一列回答的是「中出来的奖里它占多少」
     */
    private List<LotteryRecordFunnelDTO.PrizeLevelStatDTO> prizeLevels(LotteryRecordQuery queryForm, long winTotal) {
        List<StatRow> stats = StatRow.of(lotteryRecordDao.selectPrizeLevelStat(queryForm));
        Map<String, PrizeConfig> prizeMap = prizeCatalog.mapByCodes(stats.stream().map(s -> s.text("prizeCode")).toList());

        List<LotteryRecordFunnelDTO.PrizeLevelStatDTO> levelList = new ArrayList<>();
        for (StatRow stat : stats) {
            String code = stat.text("prizeCode");
            long count = stat.count("winCount");

            LotteryRecordFunnelDTO.PrizeLevelStatDTO item = new LotteryRecordFunnelDTO.PrizeLevelStatDTO();
            item.setPrizeLevel(stat.intValue("prizeLevel"));
            item.setPrizeCode(code);
            item.setWinCount(count);
            item.setWinShare(Rate.share(count, winTotal));
            // 奖品可能已被删除，那时只能显示编码
            PrizeConfig prize = code == null ? null : prizeMap.get(code);
            if (prize != null) {
                item.setPrizeName(prize.getPrizeName());
                item.setPrizeType(prize.getPrizeType());
                item.setIssuedValue(PrizeCatalog.issuedValue(prize, count));
            }
            levelList.add(item);
        }
        return levelList;
    }
}
