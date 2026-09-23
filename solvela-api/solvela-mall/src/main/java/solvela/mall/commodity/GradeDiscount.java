package solvela.mall.commodity;

/**
 * 一个人在商城享受的<b>积分折扣</b>。
 *
 * <h3>🔴 为什么是一个类型，不是一个 int</h3>
 * {@link MallPricing#points} 需要知道折扣率。如果签名收的是裸 {@code int}，
 * 那么下面这些调用<b>全都编译得过</b>，而且长得都一样：
 * <pre>
 *   MallPricing.points(sku, commodity, memberGrade);   // 传了等级，不是折扣率
 *   MallPricing.points(sku, commodity, quantity);      // 传了数量
 *   MallPricing.points(sku, commodity, 0);             // 以为「0 = 不打折」，实际是白送
 * </pre>
 * 前两个的后果是价格算成一个荒唐的数（等级 4 → 打 4 折），
 * 第三个是全场白送。三个都不报错。
 *
 * <p>包成类型之后，这些写法一个都过不了编译。
 *
 * @param gradeCode 这个人当时的等级，{@code 0} = 无等级 / 未登录。<b>只用于落订单快照</b>，
 *                  不参与算价 —— 算价只看 {@link #percent}
 * @param percent   积分折扣率 1-100，{@code 100} = 不打折。
 *                  🔴 <b>取值已经在 {@link MallGradeDiscountResolver} 里夹过</b>，
 *                  这里不再兜底：兜底会把「配置错了」变成「悄悄按原价卖」，
 *                  而那正是最难发现的一种错
 * @author alaric
 * @date 2026-09-23
 */
public record GradeDiscount(int gradeCode, int percent) {

    /** 不打折。未登录、没配折扣率、商品退出等级折扣，最终都落到它 */
    public static final GradeDiscount NONE = new GradeDiscount(0, 100);

    /** 真的会让价才算数 —— 100 与 NONE 等效 */
    public boolean applies() {
        return percent < 100;
    }
}
