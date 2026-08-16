package sa.draw.poolconfig.domain.vo;

import java.util.List;

/**
 * 抽奖工作台回显 · Tab2 奖池
 *
 * @param poolCode         奖池编码
 * @param poolName         奖池名称
 * @param prizeMappingList 池内坑位（按 sort_weight 升序，与保存时的行序一致）
 *
 * @Author alaric
 * @Date 2026-07-26
 */
public record DrawWorkbenchPoolVO(String poolCode, String poolName, List<DrawWorkbenchMappingVO> prizeMappingList) {
}
