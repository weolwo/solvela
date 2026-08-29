package solvela.base.domain;

import java.util.List;

/**
 * 分页返回对象。
 *
 * <h3>为什么没有 emptyFlag</h3>
 * 它等于 {@code list.isEmpty()}，是一份必须靠人手工维护、和 {@code list} 同步的冗余状态。
 * 冗余状态只会朝一个方向失效：手工拼空页时忘了写、或写反。前端从来没读过它，
 * 也就是说它唯一的作用是给「两边不一致」留一个入口。删掉之后这个入口不存在了。
 *
 * <h3>为什么是 record</h3>
 * 分页结果<b>拼好就该定型</b>：任何「先 new 出来再逐个 set」的写法都存在
 * 「少 set 一个字段」的中间态（{@code subListPage} 以前就漏了 pageSize，一直返回 null）。
 * record 让编译器强制五个字段一次给全，缺一个都过不了编译。
 *
 * @param pageNum  当前页，从 1 开始
 * @param pageSize 每页数量
 * @param total    总记录数
 * @param pages    总页数
 * @param list     当前页数据，永不为 null
 */
public record PageResult<T>(long pageNum, long pageSize, long total, long pages, List<T> list) {

    public PageResult {
        list = list == null ? List.of() : list;
    }

    /**
     * 空页。用在「查询条件本身就注定查不到」的短路分支上 ——
     * 那些地方不该再手工拼一遍五个字段。
     */
    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return new PageResult<>(pageNum, pageSize, 0L, 0L, List.of());
    }
}
