package solvela.admin.constant;

/**
 * 缓存 key
 *
 * @Author 1024创新实验室-主任:卓大
 * @Date 2022-01-07 18:59:22
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
public class AdminCacheConst {

    public static class Department {

        /**
         * 部门列表
         */
        public static final String DEPARTMENT_LIST_CACHE = "department_list_cache";

        /**
         * 部门树
         */
        public static final String DEPARTMENT_TREE_CACHE = "department_tree_cache";

        /**
         * 某个部门以及下级的id列表
         */
        public static final String DEPARTMENT_SELF_CHILDREN_CACHE = "department_self_children_cache";

        /**
         * 部门路径 缓存
         */
        public static final String DEPARTMENT_PATH_CACHE = "department_path_cache";

    }

    /**
     * 分类相关缓存
     */
    public static class Category {

        public static final String CATEGORY_ENTITY = "category_cache";

        public static final String CATEGORY_SUB = "category_sub_cache";

        public static final String CATEGORY_TREE = "category_tree_cache";
    }

    /**
     * 登录相关
     */
    public static class Login {

        /**
         * 请求用户信息
         */
        public static final String REQUEST_EMPLOYEE = "login_request_employee";

        /**
         * 请求用户信息权限
         */
        /**
         * 缓存名带 v2：{@code UserPermission} 从 solvela-base 搬到了 admin，而缓存里的 JSON
         * 带着旧的全限定类名。沿用旧缓存名的话，发布后第一次读会拿旧 payload 反序列化，
         * 直接 ClassNotFound —— 换个名字等于让存量条目自然过期，谁也不用去线上手工清 key。
         */
        public static final String USER_PERMISSION = "login_user_permission_v2";
    }

    /**
     * 字典缓存。原先在 solvela-base 的 {@code CacheKeyConst}，但只有管理端的字典模块用它，
     * 而字典的增删改也只发生在管理端 —— 放在共享层等于让每个端都以为自己该管它。
     */
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
