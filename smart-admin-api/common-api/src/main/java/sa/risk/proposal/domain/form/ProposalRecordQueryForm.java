package sa.risk.proposal.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import sa.base.common.domain.PageParam;

import java.time.LocalDate;

/**
 * 提案表 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-04-18 23:13:50
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class ProposalRecordQueryForm extends PageParam {

    /**
     * 会员号（精确匹配，走 member_id 索引）。
     *
     * <p>🔴 这里<b>刻意不再收账号</b>：v3.71.0 之后 {@code member_name} 只是展示快照、
     * 身上没有任何索引，拿它当查询条件必是全表扫；而它又是可改的，
     * 用户改名之后按旧名字查等于查不到 —— 「不报错，只是查不到了」正是这次换键要消灭的。
     * 后台要按账号找人，先经 {@code MemberService.getMemberId} 换成会员号。
     */
    @Schema(description = "会员号")
    private Long memberId;

    @Schema(description = "更新时间")
    private LocalDate updateTimeBegin;

    @Schema(description = "更新时间")
    private LocalDate updateTimeEnd;

    @Schema(description = "创建时间")
    private LocalDate createTimeBegin;

    @Schema(description = "创建时间")
    private LocalDate createTimeEnd;

    @Schema(description = "优惠配置ID")
    private Long promotionConfigId;

    @Schema(description = "状态：0-等待中, 10-待一审, 11-待二审, 20-驳回, 30-待执行, 40-执行中, 50-成功, 60-部分成功, 70-彻底失败, 80-风控拦截")
    private Integer status;

    @Schema(description = "来源：TASK(任务), DRAW(抽奖), MANUAL(人工)")
    private String sourceType;

    @Schema(description = "来源单号(taskRecordId 或 drawLogTraceId)")
    private String sourceBizId;

    @Schema(description = "一审人")
    private String firstReviewer;

    @Schema(description = "提案单号，服务端生成，对外唯一标识")
    private String tradeNo;

    @Schema(description = "SCORE/BALANCE/COUPON/PHYSICAL")
    private String assetType;

}
