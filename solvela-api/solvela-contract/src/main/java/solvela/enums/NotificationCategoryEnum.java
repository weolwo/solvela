package solvela.enums;

import lombok.Getter;

/**
 * 通知分类，对齐 {@code t_notification_template.category} 与 {@code t_member_notification.category}
 * —— 两列是同一个字典（后者是发送时从模板抄下来的快照）。
 *
 * <p>⚠️ 这个字典<b>只管展示与免打扰</b>：C 端 tab 分组按它分，用户的免打扰开关也按它。
 * 它<b>不参与</b>存储决策 —— 通知走写扩散、公告走读扩散，那是由「触达模型」决定的，
 * 与内容分类无关。详见 {@code docs/通知与公告-实现技术方案.md} §1。
 *
 * <p>🔴 新增分类之前先想清楚它能不能被用户关掉。加一个「谁都能关」的分类很容易，
 * 但把本该必达的通知（账号被冻结）塞进可关分类，用户关掉之后就再也收不到，
 * 而且不报错、没人发现。
 *
 * @Author alaric
 * @Date 2026-09-14
 */
@Getter
public enum NotificationCategoryEnum {

    /**
     * 系统：账号安全、冻结解冻、强制确认类。
     *
     * <p>🔴 <b>不允许用户关闭</b>。这一档的通知是「不看会出事」的那种。
     */
    SYSTEM("系统通知", false),

    /**
     * 交易：发货、履约失败、券将过期。
     *
     * <p>可关但默认开 —— 用户主动关掉之后收不到发货提醒，是他自己的选择。
     */
    TRADE("交易物流", true),

    /**
     * 营销：中奖、活动预告。
     *
     * <p>可关。这一档是最容易泛滥、也最该给用户一个开关的。
     */
    MARKETING("活动营销", true),
    ;

    private final String desc;

    /**
     * 是否允许用户在免打扰设置里关闭本分类。
     *
     * <p>放在枚举上而不是配置表里：这是个<b>产品契约</b>，不是可运营的参数。
     * 哪天要把 SYSTEM 改成可关，应该是一次需要有人 review 的代码改动，
     * 而不是后台点一下开关就生效。
     */
    private final boolean mutable;

    NotificationCategoryEnum(String desc, boolean mutable) {
        this.desc = desc;
        this.mutable = mutable;
    }
}
