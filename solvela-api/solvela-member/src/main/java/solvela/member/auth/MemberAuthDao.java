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
    /*
     * 🔴 后三列是给登录风控的，别顺手删掉：
     *   register_ip        注册那台机器的网络也算「常用网段」，否则刚注册的人第二次打开就是陌生网络
     *   create_time        last_active_time 为空（这一列上线前建的号）时的退路
     *   last_active_time   判「沉睡」用它，不能用最后登录时间（会话 30 天不续期，见 MemberActivityService）
     * 少取一列不会报错，只会让矩阵悄悄按一个错的格子判人。
     */
    @Select("""
            SELECT member_id, member_name, nickname, avatar_file_id, gender, status, password,
                   register_ip, create_time, last_active_time
            FROM t_member
            WHERE phone_hash = UNHEX(#{phoneHashHex})
            """)
    Member selectForLogin(@Param("phoneHashHex") String phoneHashHex);

    /**
     * 按邮箱摘要查登录所需信息。与 {@link #selectForLogin} 逐字对称。
     *
     * <p>🔴 <b>UNHEX 同样不能省</b>，理由完全一样：{@code email_hash} 也是 {@code binary(32)}，
     * 而 {@code PiiHasher.hash()} 返回 64 位 hex。漏了的表现是
     * 「邮箱注册成功，但登录一直说邮箱或密码错误」—— 而两边代码单看都很正常。
     *
     * <p>⚠️ 邮箱注册出来的会员<b>没有手机号</b>（{@code phone_hash} 为 NULL），
     * 反过来手机号注册的会员也没有邮箱。所以这两个查询各查各的，
     * 不要指望其中一个能兜住另一个。
     */
    @Select("""
            SELECT member_id, member_name, nickname, avatar_file_id, gender, status, password,
                   register_ip, create_time, last_active_time
            FROM t_member
            WHERE email_hash = UNHEX(#{emailHashHex})
            """)
    Member selectForLoginByEmail(@Param("emailHashHex") String emailHashHex);

    /**
     * 按会员号查身份信息。<b>不含 password</b> —— 还原登录态用不到它。
     */
    @Select("""
            SELECT member_id, member_name, nickname, avatar_file_id, gender, status
            FROM t_member
            WHERE member_id = #{memberId}
            """)
    Member selectForAuth(@Param("memberId") Long memberId);

    /**
     * 绑定邮箱要用的三列：状态、当前密码、当前邮箱密文。
     *
     * <p>单独一个查询而不是复用 {@link #selectForAuth}：那个刻意不含 password
     * （「还原登录态用不到它」），而这里两样都要 —— 换绑时要验当前密码，
     * 也要拿旧邮箱去发验证码。
     *
     * <p>⚠️ 取的是 {@code email} 密文不是 {@code email_hash}：换绑那条路要把旧邮箱
     * <b>解出来</b>才能给它发信。摘要是单向的，做不到这件事 ——
     * 这正是 {@code t_member} 「密文 + hash 双写」的用途，见 {@code PiiHasher} 的类注释。
     */
    @Select("""
            SELECT member_id, status, password, email
            FROM t_member
            WHERE member_id = #{memberId}
            """)
    Member selectForEmailBind(@Param("memberId") Long memberId);

    /**
     * 核身（登录二次验证）要用的两列：当前手机号与邮箱<b>密文</b>。
     *
     * <h3>🔴 为什么不把这两列加进 {@link #selectForLogin}</h3>
     * 那两个查询跑在<b>每一次登录请求</b>上，而它们刻意只取认证需要的列
     * （类注释：「查不到，就不可能漏」）。为了一条很少走到的分支，
     * 让所有人的手机号、邮箱每次登录都进一遍结果集和一级缓存，是个很差的交换。
     *
     * <p>所以单独一个查询，<b>只在真的要发码时</b>才调：核身路由选通道要它
     * （判「绑了没有」），发码、验码要它（解出目标）。
     *
     * <p>取密文不取 {@code *_hash}：要把号码<b>解出来</b>才能发信 / 发短信，
     * 而摘要是单向的 —— 同 {@link #selectForEmailBind}。
     */
    @Select("""
            SELECT member_id, phone, email
            FROM t_member
            WHERE member_id = #{memberId}
            """)
    Member selectContacts(@Param("memberId") Long memberId);

    /** 换绑手机号要读的那几列：状态、密码（判有没有）、当前手机号密文。 */
    @Select("""
            SELECT member_id, status, password, phone
            FROM t_member
            WHERE member_id = #{memberId}
            """)
    Member selectForPhoneBind(@Param("memberId") Long memberId);

    /**
     * 换绑手机号。
     *
     * <p>🔴 {@code UNHEX} 不能省，理由见 {@link #selectForLogin} ——
     * 漏了的表现是「绑定说成功了，但按手机号登录查不到人」。
     *
     * <p>唯一约束 {@code uk_mbr_phone_hash} 会拦住「绑一个别人已经绑了的号」，
     * 那条异常由调用方翻译成 PHONE_TAKEN。
     */
    @org.apache.ibatis.annotations.Update("""
            UPDATE t_member
               SET phone = #{phoneCipher}, phone_hash = UNHEX(#{phoneHashHex}), update_time = NOW()
             WHERE member_id = #{memberId}
            """)
    int updatePhone(@Param("memberId") Long memberId,
                    @Param("phoneCipher") String phoneCipher,
                    @Param("phoneHashHex") String phoneHashHex);

    /**
     * 取联系方式，供「账号安全」页展示。
     *
     * <p>连 {@code password} 一起查，是因为页面要知道「有没有设过密码」——
     * 换绑邮箱时没设过密码的人只能走旧邮箱验证码那条路。
     * 查出来的密码<b>只用来判空</b>，一个字符都不会离开服务端。
     */
    @Select("""
            SELECT member_id, phone, email, password
            FROM t_member
            WHERE member_id = #{memberId}
            """)
    Member selectContact(@Param("memberId") Long memberId);

    /**
     * 换绑邮箱。
     *
     * <p>🔴 {@code UNHEX} 同样不能省，理由见 {@link #selectForLogin}。
     * 漏了的表现是「绑定说成功了，但按邮箱登录查不到人」。
     *
     * <p>唯一约束 {@code uk_mbr_email_hash} 会拦住「绑一个别人已经绑了的邮箱」——
     * 查重只是提前给一句人话，真正的防线是它。
     */
    @org.apache.ibatis.annotations.Update("""
            UPDATE t_member
               SET email = #{emailCipher}, email_hash = UNHEX(#{emailHashHex}), update_time = NOW()
             WHERE member_id = #{memberId}
            """)
    int updateEmail(@Param("memberId") Long memberId,
                    @Param("emailCipher") String emailCipher,
                    @Param("emailHashHex") String emailHashHex);

    /**
     * 重置密码。
     *
     * <p>只改 password 一列。<b>不碰 status</b> —— 一个被冻结的账号不该因为
     * 改了密码就自动解冻，那正是 {@code PasswordResetFailReason.ACCOUNT_UNAVAILABLE}
     * 要在上游拦住的事。
     */
    @org.apache.ibatis.annotations.Update("""
            UPDATE t_member
               SET password = #{password}, update_time = NOW()
             WHERE member_id = #{memberId}
            """)
    int updatePassword(@Param("memberId") Long memberId, @Param("password") String password);
}
