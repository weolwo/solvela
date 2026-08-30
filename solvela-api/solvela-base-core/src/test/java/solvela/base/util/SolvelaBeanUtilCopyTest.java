package solvela.base.util;

import lombok.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 钉住 {@link SolvelaBeanUtil#copy} 与 {@link SolvelaBeanUtil#deepCopy} 在<b>嵌套集合</b>
 * 上的行为差异。
 *
 * <p>这不是为了测 Spring 或 Jackson，而是为了让「浅拷贝会把源对象的 List 引用直接塞进
 * 目标字段」这件事有一个<b>会失败的证据</b>。它在分层改造里出现过多次：
 * Form → Command、DTO → VO，只要目标类型含嵌套集合，用 copy 就会静默出错 ——
 * 编译通过、序列化正常，直到有人取出元素做强转。
 */
class SolvelaBeanUtilCopyTest {

    @Data
    public static class SrcItem {
        private String name;
    }

    @Data
    public static class DstItem {
        private String name;
    }

    @Data
    public static class Src {
        private String title;
        private List<SrcItem> items;
    }

    @Data
    public static class Dst {
        private String title;
        private List<DstItem> items;
    }

    private static Src sample() {
        SrcItem item = new SrcItem();
        item.setName("alice");
        Src src = new Src();
        src.setTitle("t");
        src.setItems(List.of(item));
        return src;
    }

    @Test
    @DisplayName("浅拷贝遇到元素类型不同的嵌套集合会直接跳过 —— 目标字段留在 null")
    void 浅拷贝在嵌套集合上静默丢数据() {
        Dst dst = SolvelaBeanUtil.copy(sample(), Dst.class);

        assertEquals("t", dst.getTitle(), "扁平字段是对的，所以问题很难被发现");

        // 🔴 这一行就是整个坑：Spring 的 BeanUtils 会解析泛型，发现
        // List<SrcItem> 与 List<DstItem> 不兼容后<b>跳过该属性</b> —— 既不报错也不转换。
        // 不是抛异常，不是类型错乱，而是那一段数据凭空消失。
        // 接口照常返回 200，前端只是少显示一块，没人会立刻发现。
        assertNull(dst.getItems(), "嵌套集合被静默丢弃，这正是它危险的地方");
    }

    @Test
    @DisplayName("深拷贝逐层按目标类型重建")
    void 深拷贝正确转换嵌套集合的元素类型() {
        Dst dst = SolvelaBeanUtil.deepCopy(sample(), Dst.class);

        assertEquals("t", dst.getTitle());
        assertEquals(1, dst.getItems().size());
        DstItem item = dst.getItems().get(0);
        assertEquals("alice", item.getName());
    }

    @Test
    @DisplayName("扁平结构两者等价 —— 所以列表装配继续用更快的 copy")
    void 扁平结构上两者一致() {
        SrcItem src = new SrcItem();
        src.setName("bob");

        assertEquals("bob", SolvelaBeanUtil.copy(src, DstItem.class).getName());
        assertEquals("bob", SolvelaBeanUtil.deepCopy(src, DstItem.class).getName());
    }

    @Test
    void 入参为空时返回null() {
        assertNull(SolvelaBeanUtil.deepCopy(null, Dst.class));
        assertNull(SolvelaBeanUtil.deepCopy(sample(), null));
    }
}
