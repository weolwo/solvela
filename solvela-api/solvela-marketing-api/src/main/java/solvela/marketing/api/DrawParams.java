package solvela.marketing.api;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * 抽奖入参 {@code params} 的契约：前端能带哪些自定义参数、每个多大（网关方案 §5.6）。
 *
 * <h3>为什么要单独管它</h3>
 * C 端网关的每个入参都是一个请求对象，对象本身就是参数白名单 —— 只有 {@code DrawRequest.params}
 * 是 {@code Map<String, Object>}，装玩法的自定义参数，原样绑进编排脚本的 {@code params} 变量。
 * 不管的话，它是全网关唯一一个「什么都能装、装多大都行」的口子：脚本里一个 {@code params.xxx}
 * 读的是谁都能填的值，而写脚本的运营未必意识到这一点。
 *
 * <h3>🔴 键必须先在 {@link #KEYS} 里声明</h3>
 * 今天没有任何玩法用到自定义参数（前端从不带 {@code params}，种子脚本也没有一处读它），所以是空集 ——
 * 带任何键都是 400。新玩法要从前端带参数时：先在这里加键，再写脚本，最后发前端。
 * 编排场景的变量契约（{@code ScriptScene.ACTIVITY_PLAY}）只声明了「有一个 Map 叫 params」，
 * 里面的键由这里声明：网关看不见脚本引擎，而这个模块两边都依赖。
 *
 * <h3>值的形状也封顶</h3>
 * 键数、字符串长度、嵌套深度。值只能是字符串、数字、布尔、null，或者由它们组成的数组 / 对象。
 *
 * @Date 2026-10-17
 */
public final class DrawParams {

    /** 前端可以带的键。见类注释：空集，带什么都拒 */
    public static final Set<String> KEYS = Set.of();

    /** 一层里最多几个键 / 一个数组最多几个元素 */
    public static final int MAX_ENTRIES = 8;

    /** 键名、字符串值最长多少个字符 */
    public static final int MAX_STRING_LENGTH = 256;

    /** 最深几层：{@code params} 本身是第 1 层，它里面的对象 / 数组是第 2 层 */
    public static final int MAX_DEPTH = 2;

    private DrawParams() {
    }

    /** 按 {@link #KEYS} 校验。返回给用户看的那句话；没问题返回 null */
    public static String violation(Map<String, Object> params) {
        return violation(params, KEYS);
    }

    /** 同上，键的白名单由调用方给（测试用：今天的白名单是空集，形状规则走不到） */
    public static String violation(Map<String, Object> params, Set<String> keys) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        if (params.size() > MAX_ENTRIES) {
            return "自定义参数最多 " + MAX_ENTRIES + " 个";
        }
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (!keys.contains(entry.getKey())) {
                return "不认识的参数 " + cut(entry.getKey());
            }
            String shape = shape(entry.getValue(), 1);
            if (shape != null) {
                return "参数 " + entry.getKey() + " " + shape;
            }
        }
        return null;
    }

    /** {@code value} 位于第 {@code depth} 层容器里 */
    private static String shape(Object value, int depth) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return null;
        }
        if (value instanceof String s) {
            return s.length() > MAX_STRING_LENGTH ? "过长" : null;
        }
        if (value instanceof Map<?, ?> || value instanceof Collection<?>) {
            if (depth >= MAX_DEPTH) {
                return "嵌套太深";
            }
            Collection<?> children = value instanceof Map<?, ?> map ? map.values() : (Collection<?>) value;
            if (children.size() > MAX_ENTRIES) {
                return "元素太多";
            }
            if (value instanceof Map<?, ?> map) {
                for (Object key : map.keySet()) {
                    if (String.valueOf(key).length() > MAX_STRING_LENGTH) {
                        return "的键名过长";
                    }
                }
            }
            for (Object child : children) {
                String shape = shape(child, depth + 1);
                if (shape != null) {
                    return shape;
                }
            }
            return null;
        }
        return "类型不支持";
    }

    /** 回显给用户的键名截短：它是用户自己填的，但没必要原样回显一整段 */
    private static String cut(String key) {
        return key == null || key.length() <= 32 ? key : key.substring(0, 32) + "…";
    }
}
