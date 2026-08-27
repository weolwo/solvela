package solvela.draw.poolconfig.domain.dto;

import java.util.List;
import lombok.Data;

/**
 * 奖池配置一览的整体返回：顶部概览 + 当前页明细。
 *
 * <p>概览与明细同一次请求算出，卡片数字与列表必然一致。
 *
 * @Author alaric
 * @Date 2026-08-16
 */
@Data
public class PrizePoolBoardResultDTO {

    /** 奖池总数（筛选后） */
    private Integer poolCount;

    /**
     * 当前真正能抽的奖池数。四个条件同时成立才算数 ——
     * 这是运营最该先看的那个数字：配了 10 个池、只有 3 个能抽，剩下 7 个都是白配的。
     */
    private Integer drawableCount;

    /** DANGER 级告警总数 */
    private Integer dangerCount;

    /** WARN 级告警总数 */
    private Integer warnCount;

    /** 当前页的奖池明细 */
    private List<PrizePoolBoardDTO> list;

    /** 奖池总数，供前端分页 */
    private Long total;
}
