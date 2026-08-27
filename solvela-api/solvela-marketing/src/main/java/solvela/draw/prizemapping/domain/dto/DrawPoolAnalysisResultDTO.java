package solvela.draw.prizemapping.domain.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 奖池概率分析的整体返回：顶部概览 + 当前页的奖池明细。
 *
 * <p>概览与明细在同一次请求里算出来，不拆成两个接口 ——
 * 两个接口就是两次独立查询，中间数据一变卡片数字就和列表对不上。
 *
 * @Author alaric
 * @Date 2026-08-16
 */
@Data
public class DrawPoolAnalysisResultDTO {

    /** 奖池总数（筛选后） */
    private Integer poolCount;

    /** 坑位总数（筛选后） */
    private Integer slotCount;

    /** DANGER 级告警总数：会导致抽奖报错或发不出奖 */
    private Integer dangerCount;

    /** WARN 级告警总数 */
    private Integer warnCount;

    /**
     * 概率未闭环的奖池数 —— 单独拎出来，因为它不是「配置可疑」而是
     * 「这个奖池现在按下抽奖就报 500」，严重程度和其他告警不是一个量级。
     */
    private Integer brokenPoolCount;

    /** 所有奖池单抽期望赔付之和，仅供横向比较 */
    private BigDecimal totalExpectedCostPerDraw;

    /** 当前页的奖池明细 */
    private List<DrawPoolAnalysisDTO> list;

    /** 奖池总数，供前端分页 */
    private Long total;
}
