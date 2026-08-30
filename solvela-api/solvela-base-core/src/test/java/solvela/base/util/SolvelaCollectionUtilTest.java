package solvela.base.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 判空口径固化。
 *
 * <p>口径必须与 {@code commons-collections4} 的 {@code CollectionUtils} / {@code MapUtils} 逐条一致 ——
 * 全项目 125 处调用，口径要是漂了（比如把"空集合"和"null"区别对待），
 * 会在一堆不相干的地方冒出静默的行为变化。这类漂移正是交接文档 §10 记过的教训。
 *
 * @Date 2026-08-08
 */
public class SolvelaCollectionUtilTest {

    @Test
    public void 集合null与空都算空() {
        assertTrue(SolvelaCollectionUtil.isEmpty((Collection<?>) null));
        assertTrue(SolvelaCollectionUtil.isEmpty(List.of()));
        assertTrue(SolvelaCollectionUtil.isEmpty(new ArrayList<>()));
        assertFalse(SolvelaCollectionUtil.isEmpty(List.of(1)));
    }

    @Test
    public void 集合isNotEmpty是isEmpty的反面() {
        assertFalse(SolvelaCollectionUtil.isNotEmpty((Collection<?>) null));
        assertFalse(SolvelaCollectionUtil.isNotEmpty(List.of()));
        assertTrue(SolvelaCollectionUtil.isNotEmpty(List.of(1)));
    }

    @Test
    public void 只含null元素的集合不算空() {
        // 判的是"有没有元素"，不是"元素有没有值"
        List<String> onlyNull = new ArrayList<>();
        onlyNull.add(null);
        assertFalse(SolvelaCollectionUtil.isEmpty(onlyNull));
        assertTrue(SolvelaCollectionUtil.isNotEmpty(onlyNull));
    }

    @Test
    public void MapNull与空都算空() {
        assertTrue(SolvelaCollectionUtil.isEmpty((Map<?, ?>) null));
        assertTrue(SolvelaCollectionUtil.isEmpty(Map.of()));
        assertTrue(SolvelaCollectionUtil.isEmpty(new HashMap<>()));
        assertFalse(SolvelaCollectionUtil.isEmpty(Map.of("k", "v")));
        assertFalse(SolvelaCollectionUtil.isNotEmpty((Map<?, ?>) null));
        assertTrue(SolvelaCollectionUtil.isNotEmpty(Map.of("k", "v")));
    }
}
