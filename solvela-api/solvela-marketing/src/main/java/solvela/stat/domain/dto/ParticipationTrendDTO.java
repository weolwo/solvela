package solvela.stat.domain.dto;

import lombok.Data;

/**
 * 参与趋势：某天某玩法类型的参与人数
 *
 * @Author weolwo
 * @Date 2026-08-02
 */
@Data
public class ParticipationTrendDTO {

    /** 日期 yyyy-MM-dd */
    private String statDate;

    /** 玩法类型：DRAW / TASK / LOTTERY */
    private String activityType;

    /** 参与人数（该类型内按 member_id 去重） */
    private Integer memberCount;
}
