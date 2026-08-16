package sa.lottery.record.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import sa.base.common.domain.PageResult;
import sa.base.common.util.SmartPageUtil;
import sa.lottery.constant.LotteryConst;
import sa.lottery.record.dao.LotteryRecordDao;
import sa.lottery.record.domain.form.LotteryRecordQueryForm;
import sa.lottery.record.domain.vo.LotteryRecordFunnelVO;
import sa.lottery.record.domain.vo.LotteryRecordVO;
import sa.prize.prizeconfig.domain.entity.PrizeConfig;
import sa.prize.prizeconfig.manager.PrizeConfigManager;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final PrizeConfigManager prizeConfigManager;

    private static final int RATE_SCALE = 4;

    /**
     * 分页查询
     */
    public PageResult<LotteryRecordVO> queryPage(LotteryRecordQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<LotteryRecordVO> list = lotteryRecordDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 购彩记录漏斗。
     *
     * <p>回答的是翻十页号码也答不出来的问题：中奖率多少、各奖级各中了几注、
     * <b>中了奖的到底发出去没有</b>。最后一个是本页独有的价值 ——
     * 中奖只是第一步，奖品要经派发链路真正到用户手上才算完。
     */
    public LotteryRecordFunnelVO funnel(LotteryRecordQueryForm queryForm) {
        Map<String, Object> row = lotteryRecordDao.selectFunnel(queryForm);
        LotteryRecordFunnelVO vo = new LotteryRecordFunnelVO();

        long total = toLong(row.get("totalCount"));
        long wait = toLong(row.get("waitCount"));
        long lose = toLong(row.get("loseCount"));
        long win = toLong(row.get("winCount"));
        long members = toLong(row.get("memberCount"));

        vo.setTotalCount(total);
        vo.setWaitCount(wait);
        vo.setLoseCount(lose);
        vo.setWinCount(win);
        vo.setMemberCount(members);
        /*
         * 中奖率的分母用「已开奖」而不是「全部」：未开奖的号码还没揭晓，
         * 把它们算进分母会让活动刚开始时的中奖率无限接近 0，那个数字没有意义。
         */
        vo.setWinRate(rate(win, lose + win));
        vo.setTicketPerMember(members == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(total).divide(BigDecimal.valueOf(members), 2, RoundingMode.HALF_UP));

        vo.setDispatchWaitCount(toLong(row.get("dispatchWaitCount")));
        vo.setDispatchedCount(toLong(row.get("dispatchedCount")));
        vo.setDispatchFailedCount(toLong(row.get("dispatchFailedCount")));

        // ---- 一致性体检：专门用来发现被手工改坏或链路出错的记录 ----
        List<String> issues = new ArrayList<>();
        long failed = toLong(row.get("dispatchFailedCount"));
        if (failed > 0) {
            issues.add("有 " + failed + " 张号码已中奖但派发失败：用户看到自己中了奖、系统也认，但奖品没发出去。"
                    + "请到「派发记录」查看失败原因并重新触发派奖");
        }
        long winButNoPrize = toLong(row.get("winButNoPrize"));
        if (winButNoPrize > 0) {
            issues.add("有 " + winButNoPrize + " 张号码已中奖但没有奖品编码：派奖时不知道该发什么，"
                    + "通常是核销时奖级规则里的 prize_code 为空");
        }
        long winButLevelNone = toLong(row.get("winButLevelNone"));
        if (winButLevelNone > 0) {
            issues.add("有 " + winButLevelNone + " 张号码标记为已中奖，奖级却是 " + LotteryConst.PRIZE_LEVEL_NONE
                    + "（未中奖占位）：用户端按奖级排序会把它们沉到最底，看起来像没中奖");
        }
        long loseButHasLevel = toLong(row.get("loseButHasLevel"));
        if (loseButHasLevel > 0) {
            issues.add("有 " + loseButHasLevel + " 张号码未中奖却带着有效奖级：与中奖状态自相矛盾，"
                    + "多半是历史数据或人工改动留下的");
        }
        long noSign = toLong(row.get("noSign"));
        if (noSign > 0) {
            issues.add("有 " + noSign + " 张号码的防篡改签名为空：这些号码无法自证真伪，"
                    + "用户申诉时拿不出凭据");
        }
        vo.setIssueList(issues);

        // ---- 奖级分布 ----
        List<Map<String, Object>> stats = lotteryRecordDao.selectPrizeLevelStat(queryForm);
        List<String> codes = stats.stream()
                .map(s -> s.get("prizeCode"))
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .distinct()
                .toList();
        Map<String, PrizeConfig> prizeMap = codes.isEmpty() ? Map.of()
                : prizeConfigManager.lambdaQuery().in(PrizeConfig::getPrizeCode, codes).list().stream()
                        .collect(Collectors.toMap(PrizeConfig::getPrizeCode, Function.identity(), (a, b) -> a));

        List<LotteryRecordFunnelVO.PrizeLevelStatVO> levelList = new ArrayList<>();
        for (Map<String, Object> stat : stats) {
            LotteryRecordFunnelVO.PrizeLevelStatVO item = new LotteryRecordFunnelVO.PrizeLevelStatVO();
            Object levelValue = stat.get("prizeLevel");
            item.setPrizeLevel(levelValue == null ? null : ((Number) levelValue).intValue());
            String code = stat.get("prizeCode") == null ? null : String.valueOf(stat.get("prizeCode"));
            item.setPrizeCode(code);
            long count = toLong(stat.get("winCount"));
            item.setWinCount(count);
            item.setWinShare(rate(count, win));
            PrizeConfig prize = code == null ? null : prizeMap.get(code);
            if (prize != null) {
                item.setPrizeName(prize.getPrizeName());
                item.setPrizeType(prize.getPrizeType());
                if (prize.getPrizeValue() != null) {
                    item.setIssuedValue(prize.getPrizeValue().multiply(BigDecimal.valueOf(count))
                            .setScale(2, RoundingMode.HALF_UP));
                }
            }
            levelList.add(item);
        }
        vo.setPrizeLevelList(levelList);
        return vo;
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
}
