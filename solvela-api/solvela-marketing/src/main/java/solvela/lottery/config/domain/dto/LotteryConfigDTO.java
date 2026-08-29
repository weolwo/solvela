package solvela.lottery.config.domain.dto;

import solvela.enums.LotteryConfigStatusEnum;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 彩票配置列表的<b>读模型</b>：mapper 的 resultMap 映射到这里，service 也返回它。
 *
 * <p>DTO 是领域能查出来的全部，VO 是某个端决定给出去的那一部分，装配在端上做。
 * C 端将来接这条玩法时写自己的 VO，不必迁就管理端的字段。完整说明见 {@code MemberWalletDTO}。
 */

@Data
public class LotteryConfigDTO {


    private Long id;

    /** 活动编码 */
    private String activityCode;

    /** 彩票编码 */
    private String lotteryCode;

    /** 彩票名称 */
    private String lotteryName;

    /** 字符集：0-9, A-Z */
    private String numberCharset;

    /** 号码长度 */
    private Integer numberLength;

    /** 号池总数 (如: 1,000,000) */
    private Integer totalCount;

    /** 状态：0-下线, 1-上线 */
    private LotteryConfigStatusEnum status;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

}
