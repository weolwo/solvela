package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 公告状态：{@code t_announcement.status}。
 *
 * <p>不复用 {@link EnableStatusEnum}：那个的措辞是「停用 / 启用」，这里是「下架 / 发布」，
 * desc 要发给前端展示，措辞不同就是业务语义不同（见 EnableStatusEnum 的「什么时候不要用它」）。
 *
 * <p>C 端读公告的 SQL（AnnouncementMapper.xml）引用的是 {@code ${@...@PUBLISHED.value}}，改取值自动跟着变；
 * 常量改名由 {@code AnnouncementVisibleSqlTest} 渲染 SQL 兜住。
 *
 * <p>没有 {@code canTransitTo}：发布 / 下架两个方向随时都合法（保存表单带状态，下架是独立接口），
 * 不涉及资金与计数，没有要挡的迁移。
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
