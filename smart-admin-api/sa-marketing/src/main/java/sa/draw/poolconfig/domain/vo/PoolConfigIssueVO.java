package sa.draw.poolconfig.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 一条奖池配置体检告警。
 *
 * @Author alaric
 * @Date 2026-08-16
 */
@Data
@AllArgsConstructor
public class PoolConfigIssueVO {

    @Schema(description = "告警编码")
    private String code;

    @Schema(description = "严重级别: DANGER-抽奖会报错或压根进不来, WARN-配置可疑但能跑")
    private String level;

    @Schema(description = "人话说明：发现了什么，以及会导致什么后果")
    private String message;

    public static PoolConfigIssueVO danger(String code, String message) {
        return new PoolConfigIssueVO(code, "DANGER", message);
    }

    public static PoolConfigIssueVO warn(String code, String message) {
        return new PoolConfigIssueVO(code, "WARN", message);
    }
}
