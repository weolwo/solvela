package net.lab1024.sa.base.sonicexcel.meta;

import java.lang.invoke.MethodHandle;
import java.util.List;

/**
 * 行对象的构造策略。导出用不到，导入（第②档）按类型分派。
 *
 * <p>做成 sealed 是为了让导入侧的 switch 能被编译器检查完整性；
 * {@link Unavailable} 这一支的存在是因为**只用于导出的 DTO 完全可以没有无参构造**
 * （比如只有 {@code @Builder + @AllArgsConstructor}），那种情况下不该在解析元数据时就报错。
 *
 * @Date 2026-08-08
 */
public sealed interface RowConstructor {

    /**
     * POJO：无参构造 + setter 逐列注入。
     */
    record PojoNoArg(MethodHandle constructor) implements RowConstructor {
    }

    /**
     * record：攒齐 Object[] 后一次性调 canonical 构造器。
     *
     * <p>要带上全部组件类型，不能只带个数 —— 没被 {@code @SonicTitle} 标注的组件也得填值，
     * 而基本类型组件填 null 会让 {@code invokeWithArguments} 直接 NPE。
     */
    record RecordCanonical(MethodHandle constructor, List<Class<?>> componentTypes) implements RowConstructor {

        public int componentCount() {
            return componentTypes.size();
        }
    }

    /**
     * 构造不出来，只能用于导出。导入时抛出，附带原因。
     */
    record Unavailable(String reason) implements RowConstructor {
    }
}
