package net.lab1024.sa.task.constant;

/**
 * 任务模块常量（铁律 3：消除魔法值）。
 *
 * @Author alaric
 * @Date 2026-08-01
 */
public final class TaskConst {

    private TaskConst() {
    }

    // ==================== t_task_config.status ====================

    /**
     * 任务配置状态：1-待生效
     */
    public static final int CONFIG_STATUS_PENDING = 1;

    /**
     * 任务配置状态：2-生效中
     */
    public static final int CONFIG_STATUS_ACTIVE = 2;

    /**
     * 任务配置状态：3-已下线
     *
     * <p>🔴 <b>运行态的订阅判据是「status != 3 且在时间窗内」，不是「status == 2」。</b>
     * 读码核实：全工程<b>没有任何地方</b>把 status 从 1 改成 2 ——
     * {@code TaskConfigService.wizardSubmit} 落的就是 1，也不存在「启用」接口。
     * 若判 {@code status == 2}，所有任务永远不会被触发，而链路看起来完全正常
     * （事件收到了、日志也打了、就是一条进度都不涨）——
     * 这正是铁律 16「前提不成立时通过和空过分不出来」的典型形状。
     *
     * <p>另外 DDL 的默认值是 0（注释里没有 0 这个取值），判 {@code != 3} 同时把它当作可用，
     * 与「活动有没有开始由业务层按起止时间实时算、不是后台开关」的既定口径一致（交接文档 §4.6③）。
     */
    public static final int CONFIG_STATUS_OFFLINE = 3;

    // ==================== t_task_record.status ====================

    /**
     * 任务记录状态：0-进行中（含「低档已发奖、最高档未达标」）
     */
    public static final int RECORD_STATUS_RUNNING = 0;

    /**
     * 任务记录状态：1-已完成（= 最高档达标）
     */
    public static final int RECORD_STATUS_COMPLETED = 1;

    /**
     * 任务记录状态：2-已发奖（= 最高档的奖也发完了，此后不再接受事件）
     */
    public static final int RECORD_STATUS_DISPATCHED = 2;

    /**
     * 任务记录状态：3-已过期
     */
    public static final int RECORD_STATUS_EXPIRED = 3;

    // ==================== t_task_record_flow.flow_type ====================

    /**
     * 流水类型：1-进度推进（已生效）
     */
    public static final int FLOW_TYPE_ADVANCE = 1;

    /**
     * 流水类型：2-事件丢弃（未生效）。discard_reason 必填
     */
    public static final int FLOW_TYPE_DISCARD = 2;

    /**
     * 流水的 task_config_id 哨兵值：事件在<b>匹配到任何任务配置之前</b>就被丢弃时使用
     * （目前只有一种情况：线程池队列打满被拒）。
     *
     * <p>用 0 而不是 null，是因为 {@code task_config_id} 在唯一索引
     * {@code uk_t_tsk_flw_evt} 里 —— NULL 在 MySQL 唯一索引中互不相等，
     * 同一个被拒事件重投几次就会留下几行，反而污染流水。
     */
    public static final long FLOW_CONFIG_ID_NONE = 0L;

    // ==================== period_key ====================

    /**
     * 无周期分片：ONCE / UNLIMITED / STREAK 都用它
     */
    public static final String PERIOD_NONE = "NONE";

    /**
     * 周期键的轮次分隔符：{@code 20260801#2} 表示 8 月 1 日的第 2 轮。
     *
     * <p>🔴 <b>第 1 轮刻意不带后缀</b>（就是裸的 {@code 20260801}）——
     * 存量记录全是裸键，加了后缀等于让它们全部失联，而 `limit_count` 绝大多数场景就是 1。
     * 只有第 2 轮起才追加，这样「不用轮次」的任务与改造前<b>完全等价</b>。
     */
    public static final String PERIOD_ROUND_SEPARATOR = "#";

    // ==================== t_task_config.target_audience ====================

    /**
     * 目标人群：全部会员（默认）
     */
    public static final String AUDIENCE_ALL = "ALL";

    /**
     * 目标人群：新会员
     */
    public static final String AUDIENCE_NEW_MEMBER = "NEW_MEMBER";

    /**
     * 目标人群：老会员
     */
    public static final String AUDIENCE_OLD_MEMBER = "OLD_MEMBER";

    // ==================== t_task_config.limit_type ====================

    /**
     * 参与频次：终身一次
     */
    public static final String LIMIT_ONCE = "ONCE";

    /**
     * 参与频次：每日重复
     */
    public static final String LIMIT_DAILY = "DAILY";

    /**
     * 参与频次：每周重复
     */
    public static final String LIMIT_WEEKLY = "WEEKLY";

    /**
     * 参与频次：无限制。
     *
     * <p>⚠️ 「无限制」指<b>轮次不限</b>，此时 {@code limit_count} 不参与判定 ——
     * 否则「无限制 + 限制 1 次」会变成「终身一次」，与它自己的名字矛盾。
     */
    public static final String LIMIT_UNLIMITED = "UNLIMITED";

    // ==================== rule_config 约定键 ====================

    /**
     * 任务类型。由 {@code TaskConfigService.wizardSubmit} 从模板强制覆写进 rule_config，防前端伪造
     */
    public static final String RULE_KEY_TASK_TYPE = "taskType";

    /**
     * 目标次数（COUNT / SIMPLE / STREAK）—— <b>契约主形态</b>
     */
    public static final String RULE_KEY_TARGET_COUNT = "targetCount";

    /**
     * 目标次数的兼容形态：存量模板 {@code FRWAYF2X6N}（每日签到）的 ui_schema 用的是这个键。
     *
     * <p>🔴 <b>rule_config 的键名是第②层的契约，不是自由文本。</b>
     * 模板作者随手起一个键名（{@code targetX}），策略层就读不到目标值 ——
     * 表现是「任务能配、进度也涨，就是永远不完成」，一条报错都没有。
     * 这正是本方案要消灭的那类静默失效。
     *
     * <p>处理方式对齐本项目 {@code visibleWhen} 的既定做法
     * （主形态 {@code {field, eq}}、兼容 {@code {key, value}}、新增强制用主形态）：
     * 这里保留一个<b>有限且写死</b>的兼容键，让存量模板不用迁移就能跑，
     * 但<b>新增模板一律用 {@link #RULE_KEY_TARGET_COUNT}</b>。
     * 兼容名单只出不进 —— 每加一个都是在削弱契约。
     */
    public static final String RULE_KEY_TARGET_DAYS_LEGACY = "targetDays";

    /**
     * 目标金额（AMOUNT）
     */
    public static final String RULE_KEY_TARGET_AMOUNT = "targetAmount";

    /**
     * 单次事件的最小金额门槛（AMOUNT，如「单笔满 100 才计入」），缺省不限
     */
    public static final String RULE_KEY_MIN_AMOUNT = "minAmount";

    /**
     * 连续型允许断档的次数：0 = 断一次即清零（STREAK）
     */
    public static final String RULE_KEY_TOLERANCE = "tolerance";

    // ==================== progress_data 约定键 ====================

    /**
     * 最近一次命中的自然日 yyyyMMdd（STREAK 的断档判据）
     */
    public static final String PROGRESS_KEY_LAST_HIT_DATE = "lastHitDate";

    /**
     * 已派发的档位列表。
     *
     * <p>⚠️ <b>它只是给运营看的展示字段，不是判据</b>，允许偶尔滞后。
     * 发奖的真正防重是 {@code t_prize_log.uk_external_biz}（幂等键 {@code recordId:stageLevel}）。
     * 判据只留一个 —— 两个判据一定会漂移，本项目已因「同一份数据两种判法」踩过坑。
     */
    public static final String PROGRESS_KEY_DISPATCHED_STAGES = "dispatchedStages";

    // ==================== t_task_prize_mapping 约定键 ====================

    /**
     * 阶段达标条件里的目标值：{@code {"target": 3}}，由 wizardSubmit 写入
     */
    public static final String STAGE_KEY_TARGET = "target";

    /**
     * 发奖策略里的值：{@code {"value": 10}}，由 wizardSubmit 写入
     */
    public static final String PRIZE_STRATEGY_KEY_VALUE = "value";

    /**
     * 发奖计算类型：固定值
     */
    public static final String PRIZE_MODE_FIXED = "FIXED";

    // ==================== 幂等键 ====================

    /**
     * 任务发奖的跨域幂等键：{@code recordId:stageLevel}。
     *
     * <p>🔴 <b>档位不能省。</b> 链路是：
     * {@code UserPrizeEvent.sourceBizId -> PrizeDispatchHandler.buildPrizeLog() ->
     * t_prize_log.external_biz_no -> UNIQUE KEY uk_external_biz(单列全局唯一)}，
     * 撞了就被 {@code catch (DuplicateKeyException)} 当作重复派发<b>静默丢弃</b>。
     * 只传 recordId 的话，阶梯任务的第二档起会被防重逻辑正确地判成「重复投递」——
     * 不抛异常、不落失败记录，只在日志里留一行 warn，运营侧表现是「用户只收到第一档奖励」。
     * 这是零并发、单线程下就必现的。详见方案 §4.3。
     */
    public static String buildSourceBizId(Long recordId, Integer stageLevel) {
        return recordId + ":" + stageLevel;
    }
}
