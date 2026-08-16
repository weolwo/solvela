package sa.base.sonicexcel.converter;

/**
 * 双向转换器，把字典翻译、枚举翻译这类脏活从 service 里剥出来。
 *
 * <p><b>线程约定</b>：实现类被当作单例缓存、会被多线程并发调用，
 * <b>不得持有可变字段</b>；但<b>允许注入 Spring 单例依赖</b>（DictService 这类本身线程安全的东西）——
 * 这正是本框架相对 EasyExcel 的差异点，见 {@link SonicConverterFactory}。
 *
 * @param <J> 实体侧类型
 * @param <E> Excel 侧类型
 * @Date 2026-08-08
 */
public interface SonicConverter<J, E> {

    /**
     * 导出：实体属性 → Excel 值。如 1 → "开启"
     */
    E exportConvert(J value, SonicContext ctx);

    /**
     * 导入：Excel 值 → 实体属性。如 "开启" → 1
     */
    J importConvert(E value, SonicContext ctx);

    /**
     * 恒等转换，注解的默认值。
     */
    final class None implements SonicConverter<Object, Object> {

        @Override
        public Object exportConvert(Object value, SonicContext ctx) {
            return value;
        }

        @Override
        public Object importConvert(Object value, SonicContext ctx) {
            return value;
        }
    }
}
