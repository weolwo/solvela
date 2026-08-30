package solvela.risk.promotiongroup.domain.dto;

import lombok.Data;
import solvela.enums.EnableStatusEnum;

import java.time.LocalDateTime;

/**
 * 优惠配置分组列表项。
 *
 * <p>{@code typeCount} / {@code enabledTypeCount} 是子表聚合出来的 ——
 * 列表上最该回答的问题是「这个组配齐了没有」，光有组名回答不了。
 *
 * @Author alaric
 * @Date 2026-08-30
 */
@Data
public class PromotionGroupDTO {

    private Long id;

    private String groupCode;

    private String groupName;

    private String remark;

    private EnableStatusEnum status;

    /** 组内已配置的资产类型数（含停用的） */
    private Integer typeCount;

    /** 组内启用中的资产类型数。与 typeCount 不等就说明有类型被关掉了 */
    private Integer enabledTypeCount;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
