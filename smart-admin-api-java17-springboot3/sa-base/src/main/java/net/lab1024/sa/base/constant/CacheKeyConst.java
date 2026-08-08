package net.lab1024.sa.base.constant;

/**
 * 缓存key常量
 *
 * @Author 1024创新实验室: 罗伊
 * @Date 2022-05-30 21:22:12
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
public class CacheKeyConst {

    public static class Dict {

        public static final String DICT_DATA = "dict_data_cache";

        /**
         * 字典标签 → 字典项的反查缓存。
         *
         * <p>刻意和 {@link #DICT_DATA} 分成两个缓存而不是共用一个加前缀：
         * 共用时 key 形如 {@code CODE_L_xxx}，而 dataValue 本身完全可能就等于 {@code L_xxx}，
         * 两种 key 会撞在一起。
         */
        public static final String DICT_DATA_LABEL = "dict_data_label_cache";

    }

}
