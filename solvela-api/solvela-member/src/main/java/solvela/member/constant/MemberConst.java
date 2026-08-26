package solvela.member.constant;

/**
 * 会员模块常量。
 *
 * <p>取值口径的唯一真源是 DDL 注释（数据库SQL脚本/member.sql），这里只是把它们搬成常量 ——
 * 铁律 3：状态/类型一律具名常量。
 *
 * <p>前端的同名枚举在 {@code solvela-admin-web/src/constants/business/member/member-const.js}，
 * 改动时两边必须一起改。
 *
 * @Date 2026-08-23
 */
public final class MemberConst {

    private MemberConst() {
    }

    // ------------------------------------------------------------------ 会员状态

    /**
     * 正常
     */
    public static final int STATUS_NORMAL = 1;
    /**
     * 冻结（风控/违规）
     */
    public static final int STATUS_FROZEN = 2;
    /**
     * 已注销。<b>终态</b> —— 注销会把 phone_hash 置 NULL 以释放号码，
     * 那个动作不可逆，所以后台不提供「从注销改回正常」的入口。
     */
    public static final int STATUS_CANCELLED = 3;

    // ------------------------------------------------------------------ 性别

    public static final int GENDER_UNKNOWN = 0;
    public static final int GENDER_MALE = 1;
    public static final int GENDER_FEMALE = 2;

    // ------------------------------------------------------------------ 登录日志状态

    /**
     * ⚠️ 与 {@code t_login_log.login_result} <b>取值相反</b>（DDL 注释里专门标了这一条）。
     * 那张是员工登录日志，这张是会员登录日志，别照着另一张的语义读这一列。
     */
    public static final int LOGIN_STATUS_FAIL = 0;
    public static final int LOGIN_STATUS_SUCCESS = 1;
    public static final int LOGIN_STATUS_LOGOUT = 2;

    // ------------------------------------------------------------------ 实名认证状态

    public static final int VERIFY_STATUS_NONE = 0;
    /**
     * 认证中 —— <b>只有这个状态能被审核</b>
     */
    public static final int VERIFY_STATUS_PENDING = 1;
    public static final int VERIFY_STATUS_VERIFIED = 2;
    public static final int VERIFY_STATUS_FAILED = 3;

    /**
     * 驳回原因长度上限，对齐 t_member_verify.fail_reason 的 varchar(255)
     */
    public static final int MAX_FAIL_REASON_LENGTH = 255;

    // ------------------------------------------------------------------ 其它

    /**
     * 运营备注长度上限，对齐 t_member.remark 的 varchar(255)
     */
    public static final int MAX_REMARK_LENGTH = 255;
}
