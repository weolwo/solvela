package solvela.member.register;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 注册要用的两个写/读操作。
 *
 * <h3>🔴 为什么手写 SQL，而不是用 {@code MemberDao.insert(Member)}</h3>
 * {@code t_member.phone_hash} 的列类型是 <b>{@code binary(32)}</b>（原始 32 字节），
 * 而 {@code PiiHasher.hash()} 返回的是 <b>64 位 hex 字符串</b>，实体字段也是 {@code String}。
 * 直接用 MyBatis-Plus 的 insert 会把 64 个字符往 32 字节的列里塞 ——
 * 严格模式下报 Data too long，非严格模式下静默截断成半个摘要。
 *
 * <p>所以在 SQL 边界上做转换：写用 {@code UNHEX}，读用 {@code UNHEX} 比较。
 * 这样 Java 侧一路都是人类可读的 hex（排查时直接能对），库里存的是紧凑的 32 字节
 * —— 与 {@code member.sql}「附六」里那段设计说明一致。
 *
 * <p>⚠️ 同样的转换在 {@code MemberAuthDao.selectForLogin} 里也必须有。
 * 两处只要有一处漏了 UNHEX，表现就是<b>注册成功但登录说「手机号或密码错误」</b>，
 * 而两边代码单看都很正常。
 */
@Mapper
public interface MemberRegisterDao {

    /**
     * 这个手机号摘要是否已被占用。
     *
     * <p>注销的账号 {@code phone_hash} 会被置 NULL 释放号码，所以查得到就是真占用，
     * 不需要再判 status。
     *
     * <p>⚠️ 这只是<b>提前给用户一句人话</b>，不是并发防线 ——
     * 查完到插入之间有窗口。真正的防线是 {@code uk_mbr_phone_hash} 唯一约束，
     * 见 {@link MemberRegisterService} 对 DuplicateKeyException 的处理。
     */
    @Select("SELECT COUNT(1) FROM t_member WHERE phone_hash = UNHEX(#{phoneHashHex})")
    int countByPhoneHash(@Param("phoneHashHex") String phoneHashHex);

    /**
     * 建会员。
     *
     * <p>{@code create_by} 不填 —— DDL 注释：「后台导入时有值，<b>自主注册为空</b>」。
     * 空值本身就是「这是用户自己注册的」这个信息，比填一个 "SYSTEM" 有用。
     */
    @Insert("""
            INSERT INTO t_member
                (member_id, member_name, nickname, gender, phone, phone_hash,
                 password, status, register_source, register_ip, create_time)
            VALUES
                (#{memberId}, #{memberName}, #{nickname}, #{gender}, #{phoneCipher}, UNHEX(#{phoneHashHex}),
                 #{password}, #{status}, #{registerSource}, #{registerIp}, NOW())
            """)
    int insertMember(@Param("memberId") Long memberId,
                     @Param("memberName") String memberName,
                     @Param("nickname") String nickname,
                     @Param("gender") int gender,
                     @Param("phoneCipher") String phoneCipher,
                     @Param("phoneHashHex") String phoneHashHex,
                     @Param("password") String password,
                     @Param("status") int status,
                     @Param("registerSource") String registerSource,
                     @Param("registerIp") String registerIp);
}
