package solvela.stat.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 首页参与统计
 *
 * @Author weolwo
 * @Date 2026-08-02
 */
@Data
public class ParticipationDTO {

    @Schema(description = "统计天数")
    private Integer days;

    @Schema(description = "日期轴，已补齐无数据的日期")
    private List<String> dateList;

    @Schema(description = "参与趋势：日期 × 玩法类型，已补 0")
    private List<ParticipationTrendDTO> trendList;

    @Schema(description = "各玩法类型参与人数（各项之和大于总人数，跨类型会重复计数）")
    private List<ParticipationByTypeDTO> byTypeList;
}
