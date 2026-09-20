package solvela.member.grade;

/**
 * 等级为什么变了。
 *
 * <h3>🔴 故意是常量不是枚举列，和 {@code t_member_grade.grade_code} 的理由相反</h3>
 * 等级本身是<b>配置</b>（运营随时加一档），所以它不能是枚举；
 * 而「为什么变」是<b>代码里写死的几条路径</b> —— 每多一种就意味着多一段
 * 会改等级的代码，那本来就得发版。运营加不出第六种变更原因来。
 *
 * <p>存成字符串而不是数字：留痕表是给客服和事后复盘看的，
 * {@code change_type='DOWNGRADE'} 一眼能懂，{@code change_type=2} 要去翻字典。
 *
 * @author alaric
 * @date 2026-09-18
 */
public final class GradeChangeType {

    /** 升级：成长值入账后当场跨过门槛。阶段 2 起有 */
    public static final String UPGRADE = "UPGRADE";

    /** 降级：期末结算按成长值纯映射掉下来。<b>只在结算里发生</b>，阶段 4 起有 */
    public static final String DOWNGRADE = "DOWNGRADE";

    /** 保级：缓冲期结束时守住了，等级没动但要留一条痕 —— 用户得看见「你保住了」。阶段 4 起有 */
    public static final String KEEP = "KEEP";

    /** 人工调整：管理端改的。{@code reason} 必填，{@code operator} 记员工 */
    public static final String MANUAL = "MANUAL";

    /** 风控扣回：作弊判定后拉回去。阶段 5+ */
    public static final String RISK_REVOKE = "RISK_REVOKE";

    private GradeChangeType() {
    }
}
