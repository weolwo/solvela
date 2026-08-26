package solvela.member.id;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 会员号发号配置。
 *
 * <pre>{@code
 * solvela:
 *   member:
 *     id:
 *       # 🔴 置换密钥。上线后【永远不能改】——改了等于换一套置换，
 *       #    新号可能撞上已经发出去的老号。像对待数据库密码一样对待它。
 *       secret-key: "改成你自己的随机串"
 *       # 号段大小：一次向 t_member_id_seq 批发多少个内部序号
 *       step: 1000
 * }</pre>
 *
 * @Date 2026-08-22
 */
@Data
@Component
@ConfigurationProperties(prefix = "solvela.member.id")
public class MemberIdProperties {

    /**
     * Feistel 置换密钥。
     *
     * <p>🔴 <b>上线后永不可改</b>。它决定了「内部序号 → 会员号」这个映射，
     * 换密钥就是换一套映射，新发的号有可能撞上历史上已经发出去的号。
     * 放配置中心并纳入备份，不要写死在代码里、也不要跟着代码库走。
     *
     * <p>刻意<b>没有默认值</b>：默认值意味着所有环境共用同一套映射，
     * 而且一旦有人忘了配，问题要等到「两个环境的数据合并」那天才暴露 ——
     * 那时已经无法补救。所以启动即校验，缺了直接起不来。
     */
    private String secretKey;

    /**
     * 号段大小：一次批发多少个内部序号到 JVM 内存。
     *
     * <p>调大 = 打库次数少、重启浪费多；调小反之。
     * 重启丢弃当前段剩余的号是<b>无害</b>的 —— 对外号经过置换本来就是跳着走的，
     * 90 亿容量里少几百个连零头都算不上。
     */
    private int step = 1000;
}
