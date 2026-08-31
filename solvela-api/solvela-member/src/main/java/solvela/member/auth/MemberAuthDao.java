package solvela.member.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import solvela.member.Member;

/**
 * 认证链路要用的会员读取。<b>只有认证用得到的两个查询</b>，不做别的。
 *
 * <p>🔴 两个方法都<b>只 select 认证需要的列</b>，不写 {@code SELECT *}。
 * 会员表上有加密的手机号、密码哈希、实名信息 —— 每个请求都把它们捞出来，
 * 意味着这些字段会进连接池的结果集、进 MyBatis 的一级缓存、
 * 也更容易被人顺手塞进某个返回给前端的对象里。查不到，就不可能漏。
 */
@Mapper
public interface MemberAuthDao {

    /**
     * 按手机号摘要查登录所需信息。
     *
     * <p>用 {@code phone_hash} 而不是 {@code phone}：手机号是加密落库的，
     * 密文每次加密都不同（AES-GCM 带随机 IV），没法用来查。摘要是确定的，且有唯一索引。
     *
     * <h3>🔴 UNHEX 不能省</h3>
     * 列类型是 {@code binary(32)}（原始 32 字节），而 {@code PiiHasher.hash()} 返回的是
     * <b>64 位 hex 字符串</b>。直接 {@code WHERE phone_hash = #{phoneHashHex}} 是拿 64 字节
     * 去比 32 字节，<b>永远不相等</b> —— 表现是「注册成功，但登录一直说手机号或密码错误」。
     *
     * <p>2026-08-31 修正。此前一直没暴露，因为在那之前<b>全仓没有任何写 phone_hash 的路径</b>，
     * {@code t_member} 是空表，这个查询从来没真正匹配过任何一行。
     * 写入侧的对称转换见 {@code MemberRegisterDao} 的 UNHEX —— 两边只要有一处漏了，
     * 症状就是登录静默失败，而两边代码单看都很正常。
     */
    @Select("""
            SELECT member_id, member_name, nickname, avatar_file_id, gender, status, password
            FROM t_member
            WHERE phone_hash = UNHEX(#{phoneHashHex})
            """)
    Member selectForLogin(@Param("phoneHashHex") String phoneHashHex);

    /**
     * 按会员号查身份信息。<b>不含 password</b> —— 还原登录态用不到它。
     */
    @Select("""
            SELECT member_id, member_name, nickname, avatar_file_id, gender, status
            FROM t_member
            WHERE member_id = #{memberId}
            """)
    Member selectForAuth(@Param("memberId") Long memberId);
}
