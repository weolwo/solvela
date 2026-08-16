package sa.base.common.util;

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
public class SmartCollectionUtilTest {

    @Test
    public void 集合null与空都算空() {
        assertTrue(SmartCollectionUtil.isEmpty((Collection<?>) null));
        assertTrue(SmartCollectionUtil.isEmpty(List.of()));
        assertTrue(SmartCollectionUtil.isEmpty(new ArrayList<>()));
        assertFalse(SmartCollectionUtil.isEmpty(List.of(1)));
    }

    @Test
    public void 集合isNotEmpty是isEmpty的反面() {
        assertFalse(SmartCollectionUtil.isNotEmpty((Collection<?>) null));
        assertFalse(SmartCollectionUtil.isNotEmpty(List.of()));
        assertTrue(SmartCollectionUtil.isNotEmpty(List.of(1)));
    }

    @Test
    public void 只含null元素的集合不算空() {
        // 判的是"有没有元素"，不是"元素有没有值"
        List<String> onlyNull = new ArrayList<>();
        onlyNull.add(null);
        assertFalse(SmartCollectionUtil.isEmpty(onlyNull));
        assertTrue(SmartCollectionUtil.isNotEmpty(onlyNull));
    }

    @Test
    public void MapNull与空都算空() {
        assertTrue(SmartCollectionUtil.isEmpty((Map<?, ?>) null));
        assertTrue(SmartCollectionUtil.isEmpty(Map.of()));
        assertTrue(SmartCollectionUtil.isEmpty(new HashMap<>()));
        assertFalse(SmartCollectionUtil.isEmpty(Map.of("k", "v")));
        assertFalse(SmartCollectionUtil.isNotEmpty((Map<?, ?>) null));
        assertTrue(SmartCollectionUtil.isNotEmpty(Map.of("k", "v")));
    }
}
