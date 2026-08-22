package sa.mall.exchangelimit.domain.form;

import sa.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商城-会员限兑计数 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-08-22 19:33:25
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class MallExchangeLimitQueryForm extends PageParam {

    @Schema(description = "会员号：关联键")
    private Long memberId;

    @Schema(description = "商品id")
    private Long commodityId;

    @Schema(description = "创建时间")
    private LocalDate createTimeBegin;

    @Schema(description = "创建时间")
    private LocalDate createTimeEnd;

}
