package solvela.draw.poolconfig.domain.command;

import lombok.Data;

import java.util.List;

/**
 * 抽奖工作台 Tab2 奖池节点（t_prize_pool_config + 其坑位映射）
 *
 * @Author alaric
 * @Date 2026-07-26
 */
@Data
public class DrawWorkbenchPoolCommand {

    /** 奖池编码，全局唯一 */
    private String poolCode;

    /** 奖池名称 */
    private String poolName;

    /** 坑位映射列表 */
    private List<DrawWorkbenchMappingCommand> prizeMappingList;
}
