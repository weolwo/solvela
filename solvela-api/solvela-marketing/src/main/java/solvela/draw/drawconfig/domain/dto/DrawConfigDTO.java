package solvela.draw.drawconfig.domain.dto;

import lombok.Data;
import solvela.enums.DrawModeEnum;
import solvela.enums.EnableStatusEnum;

import java.time.LocalDateTime;

/**
 * 抽奖配置 DTO（列表用）。
 *
 * <p>比实体多两个字段，都是<b>为了让列表页不用再发 N 次请求</b>：
 * 活动名称（只有编码的话运营认不出是哪个活动）与奖池数
 * （0 个奖池的抽奖配置是配了一半的，要能一眼看见）。
 */
@Data
public class DrawConfigDTO {

    private Long id;

    private String activityCode;

    /** 活动名称。活动被删了会是 null —— 那本身就是要被看见的异常 */
    private String activityName;

    private String drawCode;

    private String drawName;

    private DrawModeEnum drawMode;

    private String resetPeriod;

    /** 底下有几个奖池。0 表示这套抽奖还没配奖池，抽起来必然失败 */
    private Integer poolCount;

    private EnableStatusEnum status;

    private String createBy;

    private LocalDateTime updateTime;
}
