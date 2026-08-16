package sa.draw.runtime;

/**
 * Redis Lua 库存预扣结果（record 封装 Lua 返回码，消除魔法值）
 *
 * @Author alaric
 * @Date 2026-07-26
 */
public record StockDeductResult(boolean success, int code, String message) {

    public static final int CODE_SUCCESS = 1;
    public static final int CODE_NO_STOCK = -1;
    public static final int CODE_USER_LIMIT = -2;
    public static final int CODE_NOT_WARMED = -3;

    public static StockDeductResult ofLuaCode(long luaCode) {
        int code = (int) luaCode;
        return switch (code) {
            case CODE_SUCCESS -> new StockDeductResult(true, code, "预扣成功");
            case CODE_NO_STOCK -> new StockDeductResult(false, code, "库存不足");
            case CODE_USER_LIMIT -> new StockDeductResult(false, code, "超出单人限领次数");
            case CODE_NOT_WARMED -> new StockDeductResult(false, code, "库存缓存未预热");
            default -> new StockDeductResult(false, code, "未知返回码:" + code);
        };
    }
}
