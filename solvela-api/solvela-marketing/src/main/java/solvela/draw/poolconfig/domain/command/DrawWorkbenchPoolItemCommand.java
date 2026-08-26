package solvela.draw.poolconfig.domain.command;

import lombok.Data;

import java.util.List;

/**
 * 抽奖工作台 Tab1 奖项物资项（t_prize_pool_item）
 * SKU 化：名称/价值等展示信息以 prizeCode 关联 t_prize_config，本表单只收抽奖专有属性
 *
 * @Author alaric
 * @Date 2026-07-26
 */
@Data
public class DrawWorkbenchPoolItemCommand {

    /** 奖品编码，关联 t_prize_config */
    private String prizeCode;

    /** 总库存：-1 不限量 */
    private Integer totalStock;

    /** 单人限领次数：-1 不限 */
    private Integer userMaxCount;

    /** 白名单：名单内用户库存充足时必中 */
    private List<String> whiteList;
}
