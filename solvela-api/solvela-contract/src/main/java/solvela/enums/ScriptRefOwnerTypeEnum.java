package solvela.enums;

/**
 * 脚本挂在哪一类业务对象上：{@code t_script_ref.ref_type}，按枚举名存（与 {@link AnnouncementAudienceEnum} 同一个做法）。
 *
 * <p>挂载点 {@code ScriptRefPoint} 是「对象类型 + 槽位」的组合，这里只是前半段。
 * 以前它在 Java 里是 String，管理端「这个挂载点能挂到哪些对象上」的候选清单按字符串 switch，
 * 新加一类对象忘了补，表现是下拉静默变空；现在 switch 是穷举的，漏了编译不过。
 */
public enum ScriptRefOwnerTypeEnum {

    /** 任务模板，挂在 t_task_template.template_code 上 */
    TASK_TEMPLATE,

    /** 奖池，挂在 t_prize_pool_config.pool_code 上 */
    PRIZE_POOL,

    /** 活动，挂在 t_activity_config.activity_code 上 */
    ACTIVITY,

    /** 抽奖配置，挂在 t_draw_config.draw_code 上 */
    DRAW,
}
