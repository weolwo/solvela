package solvela.draw.runtime.domain;

/**
 * 执行抽奖 结果VO（record：只序列化输出，不需要框架反射 set）
 *
 * @param hit         是否中奖
 * @param prizeItemId 中奖奖项ID（未中奖为 null）
 * @param prizeCode   中奖奖品编码（未中奖为 null）
 * @param source      命中来源：PROBABILITY/WHITE_LIST/FALLBACK_DEGRADE
 * @param message     展示文案
 *
 * @Author alaric
 * @Date 2026-07-26
 */
public record DrawExecuteVO(boolean hit, Long prizeItemId, String prizeCode, String source, String message) {

    public static DrawExecuteVO ofHit(long prizeItemId, String prizeCode, String source) {
        return new DrawExecuteVO(true, prizeItemId, prizeCode, source, "恭喜中奖");
    }

    public static DrawExecuteVO ofMiss(String message) {
        return new DrawExecuteVO(false, null, null, null, message);
    }
}
