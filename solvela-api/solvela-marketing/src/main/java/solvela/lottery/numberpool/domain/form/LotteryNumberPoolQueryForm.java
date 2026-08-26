package solvela.lottery.numberpool.domain.form;

import solvela.base.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 彩票号码池 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-04-19 11:31:09
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class LotteryNumberPoolQueryForm extends PageParam {

    @Schema(description = "彩票编码")
    private String lotteryCode;

    @Schema(description = "彩票号码")
    private String ticketNumber;

    @Schema(description = "发号序列号")
    private Integer sequenceNo;

}
