package solvela.member.id;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 会员号发号序列（{@code t_member_id_seq}，单行表）。
 *
 * @Date 2026-08-22
 */
@Mapper
public interface MemberIdSeqDao {

    /**
     * 批发一个号段：把水位推进 step，并借 {@code LAST_INSERT_ID()} 把<b>推进后的值</b>
     * 记在当前连接的会话变量里。
     *
     * <p>为什么绕这一手：直接 {@code UPDATE} 完再 {@code SELECT next_seq} 是两条语句，
     * 中间有别的实例插进来推进水位的话，读到的就不是自己那一段。
     * {@code LAST_INSERT_ID(expr)} 把值挂在<b>连接</b>上，天然不受其它连接干扰。
     */
    @Update("UPDATE t_member_id_seq SET next_seq = LAST_INSERT_ID(next_seq + #{step}) WHERE id = 1")
    int advance(@Param("step") int step);

    /**
     * 取回上一步 {@code LAST_INSERT_ID(expr)} 记下的值 —— 也就是本段的<b>上界</b>。
     * 本段区间是 [返回值 - step, 返回值)。
     *
     * <p>🔴 必须和 {@link #advance} 在<b>同一个连接</b>上执行。MyBatis 默认一个
     * SqlSession 用一个连接，两个方法在同一次 Service 调用里就是同一个连接；
     * 但如果哪天中间插进了别的数据源或异步，这个前提就没了。
     */
    @Select("SELECT LAST_INSERT_ID()")
    long lastSegmentEnd();

    /**
     * 读配置的号段大小。库里的值优先于应用配置，方便运维临时调整而不用发版。
     */
    @Select("SELECT step FROM t_member_id_seq WHERE id = 1")
    Integer selectStep();
}
