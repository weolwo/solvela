package sa.base.common.constant;

import java.util.Set;

/**
 * 租户常量。
 *
 * <p><b>本类是默认租户标识与「哪些表按租户隔离」的唯一真源</b>（铁律 3：默认值一律具名常量）。
 *
 * <p>此前 {@code DEFAULT_TENANT_ID = "0"} 在三个 Service 里各写了一遍，
 * 另有四处直接硬编码 {@code setTenantId("0")} —— 共 7 个散点。
 * 默认租户标识一旦要改（比如本次 {@code "0"} → {@code "taozi"}），
 * 就得把这 7 处全找出来，<b>漏掉任何一处都会让库里同时存在两种租户值</b>，
 * 而这种数据不一致不报错、只在按租户过滤时表现为「数据凭空少了一半」。
 *
 * <p>所以收拢到这里。以后改租户标识只改这一个字面量。
 *
 * <p>⚠️ 改 {@link #DEFAULT_TENANT_ID} 时，数据库侧必须同步做两件事，且在<b>同一个维护窗口</b>内完成：
 * <ol>
 *   <li>所有表的 {@code tenant_id} 列 DEFAULT 跟着改（INSTANT DDL，毫秒级）</li>
 *   <li>{@code UPDATE t_xxx SET tenant_id = 新值 WHERE tenant_id = 旧值}</li>
 * </ol>
 * 只改一边会留下混合值 —— 参见 {@code 数据库SQL脚本/mysql/sql-update-log/v3.70.0.sql}。
 *
 * @Date 2026-08-22
 */
public class TenantConst {

    /**
     * 默认租户标识。
     *
     * <p>系统当前是单租户运行，所有业务数据都归属这个租户。
     * 取值刻意不是 {@code "0"} 这种哨兵值 —— 那会让人误以为「多租户还没启用」，
     * 于是索引里不带它、查询里不过滤它，字段长期空转。
     */
    public static final String DEFAULT_TENANT_ID = "taozi";

    /**
     * 租户列名。
     */
    public static final String TENANT_COLUMN = "tenant_id";

    /**
     * <b>按租户隔离的表白名单</b>。只有列在这里的表，SQL 才会被自动追加 {@code tenant_id = ?}。
     *
     * <p>🔴 <b>为什么是白名单而不是黑名单</b>：库里 75 张表，只有这 25 张带 {@code tenant_id}。
     * 用黑名单的话，任何一张<b>新建的、没有该列的表</b>都会被默认拦截，
     * SQL 里被塞进一个不存在的列 → {@code Unknown column 'tenant_id'}。
     * 白名单反过来：漏加只是「这张表暂时没隔离」，不会把功能打挂。
     * 两种错误的代价不对称，所以选白名单。
     *
     * <p>🔴 <b>这份清单必然会过时</b> —— 交接文档铁律 22 记着，本项目的建表语句分散在
     * 主 schema 文件和历史迁移文件两处，靠人维护清单已经漏过两次。
     * 所以 {@code TenantTableSelfCheck} 会在启动时拿它和 {@code information_schema} 对账，
     * 对不上就打醒目告警。<b>不要删掉那个自检</b>。
     */
    public static final Set<String> TENANT_TABLES = Set.of(
            // 会员域
            "t_member",
            "t_member_verify",
            "t_member_login_log",
            // 账务 / 履约
            "t_member_wallet",
            "t_member_asset_transaction",
            "t_member_coupon",
            "t_physical_delivery",
            "t_proposal_record",
            "t_promotion_config",
            // 营销 - 活动与奖品
            "t_activity_config",
            "t_activity_display",
            "t_prize_config",
            "t_prize_log",
            "t_prize_pool_config",
            "t_prize_pool_item",
            "t_pool_prize_mapping",
            "t_draw_prize_log",
            // 营销 - 任务
            "t_task_template",
            "t_task_config",
            "t_task_record",
            "t_task_record_flow",
            "t_task_event",
            "t_task_prize_mapping",
            // 营销 - 彩票
            "t_lottery_config",
            "t_lottery_issue",
            "t_lottery_prize_rule",
            "t_lottery_record"
    );

    private TenantConst() {
    }
}
