package solvela.mall.clientapi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 取图片 URL 时 file_id 为 null 不能炸。
 *
 * <h3>这条测试对应一个线上事故</h3>
 * 2026-09-05：商品分类接口 500。原因是取 URL 写成了 {@code map.get(fileId)}，
 * 而 {@code fileId} 是可空的（{@code icon_file_id} 绝大多数分类没配）。
 * 当一批 id <b>全为 null</b> 时，批量换 URL 那步返回 {@code Map.of()}，
 * 而<b>不可变 map 的 get(null) 会抛 NPE</b>。
 *
 * <p>阴险的地方在于：只要有<b>任意一个</b>分类配了图标，返回的就是 HashMap，
 * get(null) 安然无恙 —— 于是开发环境配了图的时候一切正常，
 * 而运营新建一个分类、图标留空，整个接口就挂了。
 *
 * @Date 2026-09-05
 */
class ImageUrlLookupTest {

    @Test
    @DisplayName("🔴 先证明这个坑真的存在：不可变空 map 的 get(null) 会抛")
    void 不可变空map取null键会抛() {
        Map<Long, String> immutable = Map.of();
        /*
         * ImmutableCollections.MapN.get 对空 map 显式做了 Objects.requireNonNull(key)，
         * 非空时也会去调 key.hashCode()。两条路都过不去。
         * 这条断言在于：如果哪天 JDK 改了这个行为，本测试类的存在理由就该重新审视。
         */
        assertThrows(NullPointerException.class, () -> immutable.get(null));

        // 而 HashMap 是宽容的 —— 正是这个差异让 bug 只在特定数据下出现
        assertDoesNotThrow(() -> new HashMap<Long, String>().get(null));
    }

    @Test
    @DisplayName("file_id 为 null → 给 null，不抛")
    void 空id给空URL() throws Exception {
        // 一批 id 全是 null 时，批量换 URL 那步返回的就是 Map.of()
        assertNull(urlFor(Map.of(), null));
        assertNull(urlFor(Map.of(7L, "http://x/a.png"), null));
    }

    @Test
    @DisplayName("id 有值但换不出 URL（文件被删）→ 给 null，端上画占位")
    void 查不到给空URL() throws Exception {
        // 比返回一个拼出来的 URL 好：文件没了就该没有图，而不是一个 404 的图裂
        assertNull(urlFor(Map.of(7L, "http://x/a.png"), 9L));
    }

    @Test
    @DisplayName("正常情况照常取到")
    void 正常取值() throws Exception {
        assertEquals("http://x/a.png", urlFor(Map.of(7L, "http://x/a.png"), 7L));
    }

    /** {@code urlFor} 是私有静态的，反射调 —— 它就是这条规则本身，值得单独钉住 */
    private static String urlFor(Map<Long, String> urls, Long fileId) throws Exception {
        Method method = MallClientFacade.class.getDeclaredMethod("urlFor", Map.class, Long.class);
        method.setAccessible(true);
        return (String) method.invoke(null, urls, fileId);
    }
}
