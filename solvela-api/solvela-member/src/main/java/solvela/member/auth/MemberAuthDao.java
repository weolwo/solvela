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
     */
    @Select("""
            SELECT member_id, member_name, nickname, avatar_file_id, gender, status, password
            FROM t_member
            WHERE phone_hash = #{phoneHashHex}
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
