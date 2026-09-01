package solvela.app.config;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Long 的序列化：<b>超出 JS 安全整数范围才转字符串</b>，范围内照常输出数字。
 *
 * <h3>为什么不一律转字符串</h3>
 * 一律转字符串对前端更省事，但会让所有已有接口的返回形状同时改变 ——
 * 那是一次全站联调。折中方案是只在<b>会丢精度的时候</b>转：JS 的 {@code Number}
 * 是 double，|value| 超过 2^53-1 时 {@code JSON.parse} 阶段就已经丢精度了，救不回来。
 *
 * <p>⚠️ 代价是<b>同一个字段的 JSON 类型取决于值的大小</b>，而且要等业务量上来才暴露。
 * 前端因此必须两种都收：{@code contract.ts} 的 {@code toId()} 就是那道归一化闸门，
 * 它对超出安全范围的<b>数字</b>直接抛 RangeError —— 那说明后端本该以字符串下发却没有。
 *
 * <p>2^53-1 是 JavaScript 的语言常量（{@code Number.MAX_SAFE_INTEGER}），不是本项目的选择，
 * 所以这两个数字不会漂移。与 base-core 的 {@code LongJsonSerializer} 行为一致。
 */
public final class AppLongSerializer extends ValueSerializer<Long> {

    public static final AppLongSerializer INSTANCE = new AppLongSerializer();

    private static final long JS_MIN_SAFE_INTEGER = -9007199254740991L;

    private static final long JS_MAX_SAFE_INTEGER = 9007199254740991L;

    private AppLongSerializer() {
    }

    @Override
    public void serialize(Long value, JsonGenerator gen, SerializationContext context) {
        if (value == null) {
            gen.writeNull();
            return;
        }
        if (value < JS_MIN_SAFE_INTEGER || value > JS_MAX_SAFE_INTEGER) {
            gen.writeString(Long.toString(value));
        } else {
            gen.writeNumber(value);
        }
    }
}
