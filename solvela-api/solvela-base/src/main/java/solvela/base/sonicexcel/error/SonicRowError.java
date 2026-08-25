package solvela.base.sonicexcel.error;

/**
 * 行级错误。<b>必须带行号 + 列标题 + 原始值</b> —— 只说"转换失败"等于没说。
 *
 * @param rowIndex Excel 物理行号（从 0 起，0 是表头；给用户看时记得 +1）
 * @Date 2026-08-08
 */
public record SonicRowError(int rowIndex, String title, String rawValue, String message) {

    /**
     * 给用户看的一句话，行号已转成 Excel 里肉眼可见的编号。
     */
    public String describe() {
        return "第 " + (rowIndex + 1) + " 行「" + title + "」：" + message;
    }
}
