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

    // 会员状态已迁到 solvela-model 的 MemberStatusEnum

    // ------------------------------------------------------------------ 性别

    public static final int GENDER_UNKNOWN = 0;
    public static final int GENDER_MALE = 1;
    public static final int GENDER_FEMALE = 2;

    // 实名认证状态已迁到 solvela-model 的 MemberVerifyStatusEnum

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
