package solvela.draw.drawlog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import solvela.base.domain.PageResult;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.stat.Rate;
import solvela.base.stat.StatRow;
import solvela.draw.drawlog.dao.DrawPrizeLogDao;
import solvela.draw.drawlog.domain.query.DrawPrizeLogQuery;
import solvela.draw.drawlog.domain.dto.DrawFunnelDTO;
import solvela.draw.drawlog.domain.dto.DrawPrizeLogDTO;
import solvela.prize.PrizeConfig;
import solvela.prize.prizeconfig.service.PrizeCatalog;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private final PrizeCatalog prizeCatalog;

    /**
     * 分页查询
     */
    public PageResult<DrawPrizeLogDTO> queryPage(DrawPrizeLogQuery queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<DrawPrizeLogDTO> list = drawPrizeLogDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }

    /**
     * 抽奖转化漏斗。
     *
     * <p>回答的是流水页翻十页也答不出来的问题：中奖率多少、多少人撞上「手慢了」、哪个奖发得最多。
     * 其中<b>库存不足率</b>最该被盯住 —— 它不是「没中奖」，而是「系统没东西可给」，
     * 对用户体验的含义完全不同，偏高就说明奖池缺货或兜底失效。
     */
    public DrawFunnelDTO funnel(DrawPrizeLogQuery queryForm) {
        StatRow row = StatRow.of(drawPrizeLogDao.selectFunnel(queryForm));

        DrawFunnelDTO vo = new DrawFunnelDTO();
        fillOverview(row, vo);
        vo.setPrizeHitList(prizeHits(queryForm, row.count("hitCount")));
        return vo;
    }

    /**
     * 总量与三个比率。
     *
     * <p>四种结局（中奖 / 未中奖 / 库存不足 / 异常）互斥且穷尽，加起来就是总抽奖数 ——
     * 页面上它们并排显示，对不上就是漏了一种结局没落流水。
     */
    private void fillOverview(StatRow row, DrawFunnelDTO vo) {
        long total = row.count("totalCount");
        long hit = row.count("hitCount");
        long noStock = row.count("noStockCount");
        long members = row.count("memberCount");

        vo.setTotalCount(total);
        vo.setHitCount(hit);
        vo.setMissCount(row.count("missCount"));
        vo.setNoStockCount(noStock);
        vo.setErrorCount(row.count("errorCount"));
        vo.setMemberCount(members);
        vo.setHitRate(Rate.share(hit, total));
        vo.setNoStockRate(Rate.share(noStock, total));
        vo.setDrawPerMember(Rate.average(total, members));
    }

    /**
     * 奖品维度分布：哪个奖发得最多、发出去多少价值。
     *
     * @param hitTotal 中奖总数，用作占比分母 —— 这一列回答的是「发出去的奖里它占多少」，
     *                 分母是中奖数而不是总抽奖数
     */
    private List<DrawFunnelDTO.PrizeHitDTO> prizeHits(DrawPrizeLogQuery queryForm, long hitTotal) {
        List<StatRow> hits = StatRow.of(drawPrizeLogDao.selectPrizeHit(queryForm));
        Map<String, PrizeConfig> prizeMap = prizeCatalog.mapByCodes(hits.stream().map(h -> h.text("prizeCode")).toList());

        List<DrawFunnelDTO.PrizeHitDTO> prizeHitList = new ArrayList<>();
        for (StatRow hit : hits) {
            String code = hit.text("prizeCode");
            long count = hit.count("hitCount");

            DrawFunnelDTO.PrizeHitDTO item = new DrawFunnelDTO.PrizeHitDTO();
            item.setPrizeCode(code);
            item.setHitCount(count);
            item.setHitShare(Rate.share(count, hitTotal));
            // 奖品可能已被删除，那时只能显示编码
            PrizeConfig prize = prizeMap.get(code);
            if (prize != null) {
                item.setPrizeName(prize.getPrizeName());
                item.setPrizeType(prize.getPrizeType());
                item.setIssuedValue(PrizeCatalog.issuedValue(prize, count));
            }
            prizeHitList.add(item);
        }
        return prizeHitList;
    }
}
