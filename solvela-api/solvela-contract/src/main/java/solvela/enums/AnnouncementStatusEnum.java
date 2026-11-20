package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 公告状态：{@code t_announcement.status}。
 *
 * <p>不复用 {@link EnableStatusEnum}：那个的措辞是「停用 / 启用」，这里是「下架 / 发布」，
 * desc 要发给前端展示，措辞不同就是业务语义不同（见 EnableStatusEnum 的「什么时候不要用它」）。
 *
 * <p>⚠️ C 端读公告的 SQL（AnnouncementMapper.xml）里写的是字面量 {@code status = 1}，
 * 改取值要连那里一起改。
 *
 * @Author weolwo
 * @Date 2026-11-20
 */
@Getter
@AllArgsConstructor
public enum AnnouncementStatusEnum implements BaseEnum {

    OFFLINE(0, "下架"),

    PUBLISHED(1, "发布"),
    ;

    private final Integer value;

    private final String desc;
}
