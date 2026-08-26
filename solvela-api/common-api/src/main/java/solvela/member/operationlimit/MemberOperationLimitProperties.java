package solvela.member.operationlimit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 会员操作限制的阈值配置。
 *
 * <h3>为什么不复用后台的三级等保配置</h3>
 * 等保那套（{@code t_config.level3_protect_config}）是<b>员工端</b>的策略，由后台一个开关统管。
 * 会员端沿用它有两个问题：运营调一次等保会同时改掉 C 端的风控强度（互相误伤），
 * 而且它的语义是「锁账号」——会员的手机号是可猜、可泄露的，锁账号意味着
 * <b>任何人拿别人手机号连续输错密码就能把对方挡在门外</b>。
 * 所以 C 端自己一套配置、自己一套语义（只限功能、可自助解、有到期）。
 *
 * <pre>
 * solvela:
 *   member:
 *     operation-limit:
 *       fail-max-times: 5
 *       fail-window-seconds: 1800
 *       lock-seconds: 1800
 * </pre>
 *
 * @Date 2026-08-26
 */
@Data
@Component
@ConfigurationProperties(prefix = "solvela.member.operation-limit")
public class MemberOperationLimitProperties {

    /**
     * 窗口内连续失败多少次触发限制。&lt; 1 表示<b>关闭</b>该机制（只记日志、不限制）
     */
    private int failMaxTimes = 5;

    /**
     * 失败计数的窗口长度（秒）。窗口内没再失败，计数自然过期清零
     */
    private long failWindowSeconds = 1800L;

    /**
     * 触发后限制多久（秒）。到点自动解除，无需任何人介入
     */
    private long lockSeconds = 1800L;
}
