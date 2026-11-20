package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 公告的人群类型：{@code t_announcement.audience_type}，按枚举名存（与 {@link NotificationCategoryEnum} 同一个做法），
 * JSON 里也是枚举名 —— 管理端看到的值与改成枚举前完全一样。
 *
 * <p>🔴 以前这一列在 Java 里是 String，任何字符串都能存进去。而 C 端查公告的 SQL（AnnouncementMapper.xml）
 * 只认下面三个值：拼错一个字母的公告<b>对所有人都不可见，而且不报错</b>。现在非法值在入参反序列化时就被拒。
 *
 * <p>⚠️ 加一种人群要同时改 AnnouncementMapper.xml 里两处可见性判定，否则新类型的公告谁都看不到。
 *
 * @Author weolwo
 * @Date 2026-11-20
 */
@Getter
@AllArgsConstructor
public enum AnnouncementAudienceEnum {

    ALL("全部会员"),

    /** 注册时间早于 audience_rule 里的 before */
    REGISTER_BEFORE("某时间之前注册的"),

    /** 注册时间晚于 audience_rule 里的 after */
    REGISTER_AFTER("某时间之后注册的"),
    ;

    private final String desc;
}
