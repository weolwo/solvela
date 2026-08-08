package net.lab1024.sa.base.common.util;

import java.util.Collection;
import java.util.Map;

/**
 * 集合判空。口径与 {@code commons-collections4} 的 {@code CollectionUtils} / {@code MapUtils} 完全一致。
 *
 * <p><b>为什么这次抄进来是划算的</b>：交接文档 §10.0 的铁律是「只有『除我们之外没人依赖』的库，
 * 换成自研才真的删得掉」—— 摘掉 POI 之后 commons-collections4 正好变成了这种库（878KB，只有我们在用）。
 * 而且项目里 130 处调用中有 125 处是 isEmpty / isNotEmpty，本质就是一个 null 检查，
 * 不属于「要长期维护的实现」。
 *
 * <p>刻意<b>不</b>把 {@code subtract} 之类的搬进来：那些是真算法（而且 commons 的 subtract 是
 * 按重数扣减的多重集语义，抄错了会静默出错），全项目只有 3 处，就地写清楚比包一层更安全。
 *
 * @Date 2026-08-08
 */
public final class SmartCollectionUtil {

    private SmartCollectionUtil() {
    }

    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    public static boolean isNotEmpty(Collection<?> collection) {
        return !isEmpty(collection);
    }

    public static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    public static boolean isNotEmpty(Map<?, ?> map) {
        return !isEmpty(map);
    }
}
