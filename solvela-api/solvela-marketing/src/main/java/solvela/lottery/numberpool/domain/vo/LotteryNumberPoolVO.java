package solvela.lottery.numberpool.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 彩票号码池 列表VO
 *
 * @Author weolwo
 * @Date 2026-04-19 11:31:09
 * @Copyright weolwo
 */

@Data
public class LotteryNumberPoolVO {


    @Schema(description = "id")
    private Long id;

    @Schema(description = "彩票编码")
    private String lotteryCode;

    @Schema(description = "彩票号码")
    private String ticketNumber;

    @Schema(description = "发号序列号")
    private Integer sequenceNo;

}
