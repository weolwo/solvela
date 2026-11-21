package solvela.observe;

/**
 * 设计出来的降级点：失败了不上抛、主流程照走的地方。每一处都必须计数，不能只留一行日志。
 *
 * <p>全局宪法「三、规则显式化」第 3 条：降级要看得见 —— 除了日志，还要有指标、健康检查或后台页面中的至少一个。
 * 本枚举的每个值对应一个指标序列 {@code solvela_degraded_total{point="<枚举名>"}}，
 * 面板在 {@code deploy/monitoring/dashboards/solvela-app.json} 的「设计降级」一行。
 *
 * <p>记录：后端用 {@code solvela.base.observe.Degradations#record}，网关用 {@code solvela.app.observe.Degradations#record}
 * （网关 classpath 上没有 solvela-base，两份实现共用这里的 {@link #METER} 与 {@link #TAG}）。
 *
 * <p>⚠️ 这里是枚举而不是字符串：拼错的点位名会变成一条谁也不看的新序列，而且不报错。
 * {@code DegradePointUsageTest} 断言每个值在生产代码里至少被用到一次，免得枚举与代码各走各的。
 */
public enum DegradePoint {

    // ------------------------------------------------------------------ 派奖、抽奖、任务、商城

    /** 派发线程池里的事件处理器抛错，只落了日志（主事务早已提交） */
    EVENT_HANDLER_FAILED("事件处理器异常"),
    /** 事件分类认不出或没有注册路由，事件被丢弃 */
    EVENT_UNROUTED("事件无处理器"),
    /** 派奖时奖品配置已不存在或已停用：用户中了奖，这个奖没发 */
    PRIZE_CONFIG_MISSING_AT_DISPATCH("派奖时奖品配置缺失"),
    /** 发奖记录的状态回写失败，状态停在旧值 */
    PRIZE_STATUS_WRITEBACK_FAILED("发奖记录状态回写失败"),
    /** 入账结果回写发奖记录失败，发奖记录与提案状态可能不一致 */
    PRIZE_RESULT_WRITEBACK_FAILED("入账结果回写失败"),
    /** 发奖对账任务重投失败，等下一轮 */
    PRIZE_RECONCILE_RETRY_FAILED("发奖对账重投失败"),
    /** 提案终态回写失败，提案停在「执行中」 */
    PROPOSAL_STATUS_WRITEBACK_FAILED("提案状态回写失败"),
    /** 预算回滚失败，预算水位虚高 */
    BUDGET_RELEASE_FAILED("预算回滚失败"),
    /** 抽奖幂等结果回放失败，重试拿到的是 DUPLICATE_REQUEST */
    DRAW_REPLAY_FAILED("抽奖结果回放失败"),
    /** 抽奖结果没写进幂等键，这一批重试会拿到 DUPLICATE_REQUEST */
    DRAW_RESULT_CACHE_FAILED("抽奖结果写缓存失败"),
    /** Redis 预扣的库存没补回去，库存出现漂移 */
    DRAW_STOCK_COMPENSATION_FAILED("抽奖预扣补偿失败"),
    /** 奖项白名单 JSON 解析不了，回显或看板按空 / 未知处理 */
    DRAW_WHITELIST_UNPARSEABLE("白名单解析失败"),
    /** 业务动作没投递进任务系统，这次进度不涨（对账任务会补推） */
    TASK_ACTION_DELIVERY_FAILED("任务打点投递失败"),
    /** 任务打点对账补推失败，等下一轮 */
    TASK_ACTION_RECONCILE_FAILED("任务打点对账失败"),
    /** 取会员等级 / 新老会员失败，人群条件按「未知」处理，相关任务本次丢弃或不展示 */
    TASK_MEMBER_INFO_UNAVAILABLE("任务取会员信息失败"),
    /** 任务事件处理异常，本次进度丢失 */
    TASK_EVENT_HANDLE_FAILED("任务事件处理失败"),
    /** 任务推进失败，落成丢弃 */
    TASK_ADVANCE_FAILED("任务推进失败"),
    /** 被拒事件的丢弃流水没写进去 */
    TASK_REJECTED_FLOW_WRITE_FAILED("任务丢弃流水写入失败"),
    /** 已派档位这个展示字段回写失败（不影响派发） */
    TASK_STAGE_DISPLAY_WRITEBACK_FAILED("任务档位展示回写失败"),
    /** 任务事件或模板的 schema 解析不了，该字段降级为空或模板被剔除 */
    TASK_CONFIG_UNPARSEABLE("任务配置解析失败"),
    /** 成长值入账失败，这次不涨（对账任务会补） */
    GROWTH_ACCRUAL_FAILED("成长值入账失败"),
    /** 商城订单履约异常，单子停在待履约 */
    MALL_FULFILL_FAILED("商城履约失败"),
    /** SKU 规格 JSON 解析不了，按空规格展示 */
    MALL_SKU_ATTRS_UNPARSEABLE("SKU 规格解析失败"),
    /** 奖品 ext 不是合法 JSON，图片引用没登记 —— 那张图会被孤儿清理当垃圾删掉 */
    FILE_REFERENCE_SKIPPED("文件引用登记跳过"),

    // ------------------------------------------------------------------ 风控与安全

    /** 风控整体执行失败，按放行处理 */
    RISK_FAIL_OPEN("风控失败放行"),
    /** 单条风控规则执行异常，按未命中处理 */
    RISK_RULE_ERROR("风控规则执行异常"),
    /** 风控规则配置读不出来，当作关闭 */
    RISK_RULE_CONFIG_BROKEN("风控规则配置损坏"),
    /** 风控取信号（计数、设备信任、老设备）失败，当作没有 */
    RISK_SIGNAL_UNAVAILABLE("风控信号不可用"),
    /** 风控决策流水 / 命中明细没写进去 */
    RISK_AUDIT_WRITE_FAILED("风控流水写入失败"),
    /** 安全检查（处罚、处置档、二次验证、资产出口）读状态失败，放行 */
    SECURITY_FAIL_OPEN("安全检查失败放行"),
    /** 网关限流某个维度算不出来或脚本执行失败，放行这个维度 */
    RATE_LIMIT_FAIL_OPEN("限流失败放行"),
    /** 安全状态（冷静期、冷却、待确认、观察档、封禁镜像、失败来源、操作限制、被挡计数）读写失败 */
    SECURITY_STATE_STORE_FAILED("安全状态读写失败"),
    /** 处罚没写成（蜜罐、陷阱命中但没罚到） */
    SECURITY_PENALTY_WRITE_FAILED("处罚写入失败"),
    /** 安全事件没投递出去或处理时被丢弃，只留在日志里 */
    SECURITY_EVENT_DROPPED("安全事件丢失"),
    /** 安全事件落库失败，留在处理中等下一轮 */
    SECURITY_EVENT_DRAIN_FAILED("安全事件落库失败"),
    /** 安全闸 / 限流 / 名单配置解不开，用默认值或跳过该条 */
    SECURITY_CONFIG_INVALID("安全配置无效"),
    /** 安全提醒（新设备、可疑登录、密码泄露）没发出去 */
    SECURITY_NOTICE_FAILED("安全提醒发送失败"),
    /** 验证码（短信、邮件）没发出去 */
    VERIFY_CODE_SEND_FAILED("验证码发送失败"),
    /** 联系方式解密失败，按没有处理 */
    PII_DECRYPT_FAILED("联系方式解密失败"),
    /** 客户端上报的取值（设备端、注册来源）认不出，落成 UNKNOWN，登录 / 注册照常 */
    CLIENT_VALUE_UNRECOGNIZED("客户端上报值认不出"),

    // ------------------------------------------------------------------ 基础设施与旁路

    /** 配置快照 / 名单 / 模板缓存重载失败，继续用旧的 */
    CONFIG_RELOAD_FAILED("配置重载失败"),
    /** 缓存读写失败，回源或跳过 */
    CACHE_FALLBACK("缓存降级回源"),
    /** 网关聚合接口里有一项取不到，给了 null */
    GATEWAY_PARTIAL_RESPONSE("网关聚合缺项"),
    /** 网关向后台上报路由或已加载版本失败 */
    GATEWAY_REPORT_FAILED("网关上报失败"),
    /** 网关快照发布失败，由定时任务补发 */
    GATEWAY_SNAPSHOT_PUBLISH_FAILED("网关快照发布失败"),
    /** 站内通知发送失败 */
    NOTIFICATION_SEND_FAILED("通知发送失败"),
    /** 通知偏好查询失败，按「接收」处理 */
    NOTIFICATION_PREFERENCE_UNAVAILABLE("通知偏好不可用"),
    /** 通知参数或模板参数清单解析不了，按空 / 不校验处理 */
    NOTIFICATION_TEMPLATE_UNPARSEABLE("通知参数解析失败"),
    /** 审计类日志（登录日志、操作日志、数据变更日志）没写进去或缺字段 */
    AUDIT_LOG_WRITE_FAILED("审计日志写入失败"),
    /** 任务调度基础设施异常（自举、对账、扫描、执行记录落库、消息处理） */
    JOB_INFRA_FAILED("调度基础设施异常"),
    /** 调度车道已满，任务被拒 */
    JOB_LANE_REJECTED("调度车道已满"),
    /** 消息消费记账失败，幂等保护可能缺一条 */
    MQ_LEDGER_WRITE_FAILED("消息记账失败"),
    /** 分区维护失败，下个月的分区可能没建 */
    PARTITION_MAINTAIN_FAILED("分区维护失败"),
    /** 分布式锁释放失败 */
    LOCK_RELEASE_FAILED("锁释放失败"),
    /** 孤儿文件清理失败，留到下一轮 */
    FILE_ORPHAN_CLEAN_FAILED("孤儿文件清理失败"),
    /** 脚本种子导入时有文件读不了或解析失败，被跳过 */
    SCRIPT_SEED_IMPORT_FAILED("脚本种子导入失败"),
    /** 会员 / 设备的活跃时间刷新失败 */
    ACTIVITY_REFRESH_FAILED("活跃时间刷新失败"),
    /** 字段反序列化失败，退回原文 */
    DESERIALIZE_FALLBACK("反序列化退回原文");

    /** 指标名。Prometheus 里是 {@code solvela_degraded_total} */
    public static final String METER = "solvela.degraded";

    /** 区分点位的标签名 */
    public static final String TAG = "point";

    private final String desc;

    DegradePoint(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
