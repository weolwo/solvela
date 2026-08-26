package solvela.draw.drawlog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import solvela.base.domain.PageResult;
import solvela.base.dao.SolvelaPageUtil;
import solvela.draw.drawlog.dao.DrawPrizeLogDao;
import solvela.draw.drawlog.domain.form.DrawPrizeLogQueryForm;
import solvela.draw.drawlog.domain.vo.DrawFunnelVO;
import solvela.draw.drawlog.domain.vo.DrawPrizeLogVO;
import solvela.prize.PrizeConfig;
import solvela.prize.prizeconfig.manager.PrizeConfigManager;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 抽奖流水 Service —— <b>只读</b>。
 *
 * <h3>⚠️ add / update / batchDelete / delete 已刻意移除</h3>
 * 抽奖流水是<b>发奖凭证与对账依据</b>：用户说「我明明抽中了」、财务对「这个月发了多少奖」，
 * 依据都是这张表。后台能改能删，等于这套审计不存在。
 *
 * <p>而且这四个接口从来就没有正当用途：流水由 {@code DrawExecuteService} 在抽奖链路里写入，
 * 没有任何场景需要人工补录或修改一条抽奖记录。生成器把它们一并产出来了而已。
 *
 * <p>数据要清理（比如压测后重跑基线）走 DBA 脚本，不该从后台界面点 ——
 * 见「抽奖模块-联调造数.sql」的清场章节，那里还要求同时清 Redis 的库存与限领计数，
 * 光删流水表反而会留下更不一致的状态。
 *
 * @Author weolwo
 * @Date 2026-04-19 01:22:38
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class DrawPrizeLogService {

    private final DrawPrizeLogDao drawPrizeLogDao;
    private final PrizeConfigManager prizeConfigManager;

    private static final int RATE_SCALE = 4;

    /**
     * 分页查询
     */
    public PageResult<DrawPrizeLogVO> queryPage(DrawPrizeLogQueryForm queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<DrawPrizeLogVO> list = drawPrizeLogDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }

    /**
     * 抽奖转化漏斗。
     *
     * <p>回答的是流水页翻十页也答不出来的问题：中奖率多少、多少人撞上「手慢了」、哪个奖发得最多。
     * 其中<b>库存不足率</b>最该被盯住 —— 它不是「没中奖」，而是「系统没东西可给」，
     * 对用户体验的含义完全不同，偏高就说明奖池缺货或兜底失效。
     */
    public DrawFunnelVO funnel(DrawPrizeLogQueryForm queryForm) {
        Map<String, Object> row = drawPrizeLogDao.selectFunnel(queryForm);
        DrawFunnelVO vo = new DrawFunnelVO();
        long total = toLong(row.get("totalCount"));
        long hit = toLong(row.get("hitCount"));
        long noStock = toLong(row.get("noStockCount"));
        long members = toLong(row.get("memberCount"));

        vo.setTotalCount(total);
        vo.setHitCount(hit);
        vo.setMissCount(toLong(row.get("missCount")));
        vo.setNoStockCount(noStock);
        vo.setErrorCount(toLong(row.get("errorCount")));
        vo.setMemberCount(members);
        vo.setHitRate(rate(hit, total));
        vo.setNoStockRate(rate(noStock, total));
        vo.setDrawPerMember(members == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(total).divide(BigDecimal.valueOf(members), 2, RoundingMode.HALF_UP));

        // 奖品名称与单价来自资产大库；奖品可能已被删除，那时只能显示编码
        List<Map<String, Object>> hits = drawPrizeLogDao.selectPrizeHit(queryForm);
        List<String> codes = hits.stream().map(h -> String.valueOf(h.get("prizeCode"))).toList();
        Map<String, PrizeConfig> prizeMap = codes.isEmpty() ? Map.of()
                : prizeConfigManager.lambdaQuery().in(PrizeConfig::getPrizeCode, codes).list().stream()
                        .collect(Collectors.toMap(PrizeConfig::getPrizeCode, Function.identity(), (a, b) -> a));

        List<DrawFunnelVO.PrizeHitVO> prizeHitList = new ArrayList<>();
        for (Map<String, Object> h : hits) {
            DrawFunnelVO.PrizeHitVO item = new DrawFunnelVO.PrizeHitVO();
            String code = String.valueOf(h.get("prizeCode"));
            long count = toLong(h.get("hitCount"));
            item.setPrizeCode(code);
            item.setHitCount(count);
            // 分母用中奖数而非总抽奖数：这一列回答的是「发出去的奖里它占多少」
            item.setHitShare(rate(count, hit));
            PrizeConfig prize = prizeMap.get(code);
            if (prize != null) {
                item.setPrizeName(prize.getPrizeName());
                item.setPrizeType(prize.getPrizeType());
                if (prize.getPrizeValue() != null) {
                    item.setIssuedValue(prize.getPrizeValue().multiply(BigDecimal.valueOf(count))
                            .setScale(2, RoundingMode.HALF_UP));
                }
            }
            prizeHitList.add(item);
        }
        vo.setPrizeHitList(prizeHitList);
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
