package solvela.member;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import solvela.enums.SecurityListDimensionEnum;
import solvela.enums.SecurityListStatusEnum;
import solvela.enums.SecurityListTypeEnum;

import java.time.LocalDateTime;

/**
 * 安全名单：网关与登录风控共用，表结构以 docs/架构/网关阶梯限流-技术方案.md §6.3 为准。撤销不删行（{@code status = 0} + 撤销人、原因、时间），谁加的、谁解的都查得到。
 *
 * @Date 2026-10-15
 */
@Data
@TableName("t_security_list")
public class SecurityList {

    @TableId(type = IdType.AUTO)
    private Long id;

    private SecurityListTypeEnum listType;

    private SecurityListDimensionEnum dimension;

    /** IP / CIDR 原文；身份摘要存十六进制；会员存会员号 */
    private String listValue;

    /** 灰名单命中加的分 */
    private Integer score;

    private String reason;

    /** 1-人工, 2-自动 */
    private Integer source;

    /** 自动入名单的来源：HONEYPOT / CANARY / KEYWORD / RATE_LIMIT / ESCALATE / SEGMENT。人工条目为 null */
    private String sourceRule;

    /** 有效期内命中次数。新增时留 null，取库里的默认值 1 */
    private Integer hitCount;

    /** 最近一次命中。人工条目为 null */
    private LocalDateTime lastHitTime;

    /** 最近一次证据，指向 {@code t_security_event.id} */
    private Long lastEventId;

    /**
     * 改名前的那一列，与 {@link #lastEventId} 同值。
     *
     * <p>TEMP(安全事件改名-收口): 只为发版间隙里的旧版后台保留；新代码只读 {@link #lastEventId}。
     * 移除条件同 SecurityListWriter 里那段双写。
     */
    @Deprecated
    private Long lastIncidentId;

    /** 封禁期间被安全闸直接挡掉的请求数，定时从 Redis 计数器同步。新增时留 null，取库里的默认值 0 */
    private Long blockedCount;

    /**
     * 🔴 没有公开 setter：后台新增经 {@link #startValid} 落为有效，之后只走带旧状态条件的 UPDATE
     * （状态机见 {@link SecurityListStatusEnum#canTransitTo}）。MyBatis 读库时直接写字段，不需要 setter。
     */
    @Setter(AccessLevel.NONE)
    private SecurityListStatusEnum status;

    /** null = 长期 */
    private LocalDateTime expireTime;

    private String operator;

    private String revokeOperator;

    private String revokeReason;

    private LocalDateTime revokeTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 落为<b>有效</b>：后台人工新增的条目。自动条目由 {@code SecurityListWriter} 用 SQL 直接写。
     */
    public void startValid() {
        if (status != null) {
            throw new IllegalStateException("名单条目 " + id + " 已是 " + status + "，不能再定初始状态");
        }
        this.status = SecurityListStatusEnum.VALID;
    }
}
