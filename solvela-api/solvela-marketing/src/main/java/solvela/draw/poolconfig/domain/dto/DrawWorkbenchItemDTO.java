package solvela.draw.poolconfig.domain.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 抽奖工作台回显 · Tab1 奖项物资项
 * SKU 化：prizeName/prizeType/prizeValue 来自资产大库 t_prize_config（只读展示），
 * totalStock/userMaxCount/whiteList 才是抽奖专有的可配置属性
 *
 * @param prizeCode    奖品编码
 * @param prizeName    奖品名称（资产大库快照，只读）
 * @param prizeType    资产类型（资产大库快照，只读）
 * @param prizeValue   奖励价值（资产大库快照，只读）
 * @param totalStock   总库存：-1 不限量
 * @param userMaxCount 单人限领次数：-1 不限
 * @param usedStock    跨奖池累计已发放数量（服务端权威值，前端只读）
 * @param whiteList    白名单 UserID 列表
 * @Author alaric
 * @Date 2026-07-26
 */
public record DrawWorkbenchItemDTO(String prizeCode,
                                  String prizeName,
                                  String prizeType,
                                  BigDecimal prizeValue,
                                  Integer totalStock,
                                  Integer userMaxCount,
                                  Integer usedStock,
                                  List<String> whiteList) {
}
