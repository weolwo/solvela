package net.lab1024.sa.base.sonicexcel.error;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 一次性读取的结果：<b>数据和错误一起返回</b>。
 *
 * <p>这是整个导入体验的关键 —— 用户传 500 行、30 行有问题，界面能说清楚是哪 30 行、
 * 每行错在哪一列，而不是回一句"成功导入 470 条"让人自己去猜。
 *
 * @Date 2026-08-08
 */
public record SonicReadResult<T>(List<T> data, List<SonicRowError> errors) {

    public boolean hasError() {
        return !errors.isEmpty();
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    /**
     * 拼一句能直接回给前端的话，最多列举前 n 条。
     */
    public String describeErrors(int limit) {
        if (errors.isEmpty()) {
            return "";
        }
        String head = errors.stream().limit(limit).map(SonicRowError::describe).collect(Collectors.joining("；"));
        return errors.size() <= limit ? head : head + "……等 " + errors.size() + " 处";
    }
}
