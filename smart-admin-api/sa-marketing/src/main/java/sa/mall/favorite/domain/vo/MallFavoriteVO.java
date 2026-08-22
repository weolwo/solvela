package sa.mall.favorite.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 商城-商品收藏 列表VO
 *
 * @Author weolwo
 * @Date 2026-08-22 19:34:44
 * @Copyright weolwo
 */

@Data
public class MallFavoriteVO {


    @Schema(description = "id")
    private Long id;

    @Schema(description = "会员号：关联键")
    private Long memberId;

    @Schema(description = "商品id（商品粒度，不是SKU粒度）")
    private Long commodityId;

    @Schema(description = "收藏时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
