package solvela.mall.exchangelimit.domain.dto;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 商城-会员限兑计数 列表VO
 *
 * @Author weolwo
 * @Date 2026-08-22 19:33:25
 * @Copyright weolwo
 */

@Data
public class MallExchangeLimitDTO {


    /** id */
    private Long id;

    /** 会员号：关联键 */
    private Long memberId;

    /** 商品id */
    private Long commodityId;

    /** 周期标识：NONE(终身) / 20260819(日) / 2026W34(周) / 202608(月)。取值口径对齐 t_task_record.period_key */
    private String periodKey;

    /** 该周期内已兑件数 */
    private Integer usedCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

}
