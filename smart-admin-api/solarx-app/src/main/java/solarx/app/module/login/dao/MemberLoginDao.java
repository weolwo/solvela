package solarx.app.module.login.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import sa.member.domain.entity.Member;

/**
 * 会员登录专用查询。
 *
 * <h3>为什么不加在 common-api 的 {@code MemberDao} 上</h3>
 * 那个 Dao 的类注释里有一条明确的红线：「查询一律走 member_id / member_name 的索引，
 * 不要在这里加方法」—— 它是管理端会员管理的只读查询集合。
 * 登录查询走的是第三条索引（{@code uk_mbr_phone_hash}）、且要取出
 * {@code password} 这个<b>只有鉴权链路才该碰</b>的列，混进去会让「谁能读到密码哈希」
 * 这件事失去边界。放在本模块里，读密码列的代码就只有登录这一条路径。
 *
 * <h3>🔴 binary(32) 列一律在 SQL 里转 hex，不要映射成 Java String</h3>
 * {@code phone_hash} 是 {@code binary(32)}。让 MyBatis 直接把它读成 String，
 * 会经历一次「按连接字符集解码」——摘要里那些不是合法 UTF-8 序列的字节会被替换成 {@code ?}，
 * 写进去和读出来不是同一个值，而且只在部分摘要上发生。
 * 所以约定：Java 侧只有 hex 字符串（{@code PiiHasher} 的输出），
 * 进出库时用 {@code UNHEX()} / {@code HEX()} 转换，二进制不进入 Java 类型系统。
 *
 * @Date 2026-08-25
 */
@Mapper
public interface MemberLoginDao {

    /**
     * 按手机号摘要取登录所需信息。查不到返回 null。
     *
     * <p>🔴 <b>只 select 登录用得到的列</b>。别图省事写 {@code SELECT *}：
     * 那会把 {@code phone} / {@code email} 两列密文一并读进来，
     * 于是每次登录都在内存里多一份 PII，而登录流程一个字节都用不上它们。
     *
     * @param phoneHashHex {@code PiiHasher.hash} 的输出（64 位小写 hex），
     *                     调用方必须先用 {@code MemberPhoneUtil.normalize} 规范化手机号
     */
    @Select("""
            SELECT member_id, member_name, nickname, avatar_file_id, gender, status, password
              FROM t_member
             WHERE phone_hash = UNHEX(#{phoneHashHex})
            """)
    Member selectForLoginByPhoneHash(@Param("phoneHashHex") String phoneHashHex);

    /**
     * 按会员号取鉴权所需信息。供拦截器在每个请求上还原登录态用（结果有缓存，
     * 见 {@code MemberLoginManager}），所以这里同样不取密码之外的敏感列。
     *
     * <p>⚠️ 与上面那个方法的列清单<b>刻意不同</b>：这里不要 {@code password}。
     * 鉴权时手上已经有合法 token 了，再把密码哈希捞出来放进缓存对象没有任何用处，
     * 只是让它多一个泄露面。
     */
    @Select("""
            SELECT member_id, member_name, nickname, avatar_file_id, gender, status
              FROM t_member
             WHERE member_id = #{memberId}
            """)
    Member selectForAuthById(@Param("memberId") Long memberId);
}
