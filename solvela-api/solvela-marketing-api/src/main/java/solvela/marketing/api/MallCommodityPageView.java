package solvela.marketing.api;

import java.util.List;

/**
 * 分页结果。
 *
 * <p>刻意在契约里自己定一个，<b>不复用 {@code PageResult}</b> ——
 * 那个类在 solvela-base-core 里，而本模块的 pom 只允许依赖 contract 与 spring-web
 *（「加第三个之前先想清楚它会不会跟着流到网关」）。
 *
 * @param total 总条数。C 端要显示「共 N 件」，也用来判断还有没有下一页
 */
public record MallCommodityPageView(
        List<MallCommodityBriefView> list,
        long total) {
}
