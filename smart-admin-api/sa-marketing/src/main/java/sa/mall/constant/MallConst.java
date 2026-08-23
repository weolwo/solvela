package sa.mall.constant;

import java.time.LocalDateTime;

/**
 * 商城模块常量。
 *
 * <p>取值口径的唯一真源是 DDL 注释（数据库SQL脚本/mall.sql），这里只是把它们搬成常量 ——
 * 铁律 3：状态/类型一律具名常量，散在各处的字面量迟早对不上。
 *
 * <p>前端的同名枚举在 {@code smart-admin-web/src/constants/business/mall/mall-commodity-const.js}，
 * 改动时两边必须一起改。
 *
 * @Date 2026-08-23
 */
public final class MallConst {

    private MallConst() {
    }

    // ------------------------------------------------------------------ 商品状态

    /** 下架 */
    public static final int COMMODITY_STATUS_OFF = 0;
    /** 上架 */
    public static final int COMMODITY_STATUS_ON = 1;
    /** 草稿：新建默认落这里，对齐 DDL 的 DEFAULT 2 */
    public static final int COMMODITY_STATUS_DRAFT = 2;

    // ------------------------------------------------------------------ SKU 状态

    public static final int SKU_STATUS_DISABLED = 0;
    public static final int SKU_STATUS_ENABLED = 1;

    // ------------------------------------------------------------------ 分类状态

    public static final int CATEGORY_STATUS_DISABLED = 0;
    public static final int CATEGORY_STATUS_ENABLED = 1;

    /**
     * 分类批量新建的单次上限（父 + 子一起算）。
     *
     * <p>不是技术限制，是表单本身的限制：一屏放不下几十行，运营录到一半滚屏找位置
     * 比分两次提交更容易出错。超了就分批，反正批量本来就是为了省点击次数。
     */
    public static final int MAX_CATEGORY_BATCH = 10;

    // ------------------------------------------------------------------ 支付方式

    /** 纯积分：cash_price 恒为 0 */
    public static final int PAY_TYPE_POINTS = 1;
    /** 积分 + 现金 */
    public static final int PAY_TYPE_POINTS_CASH = 2;

    // ------------------------------------------------------------------ 订单状态

    /**
     * 取值是<b>跳跃的（0/10/20/...）</b>，不是连续序号 —— DDL 里就是这么定的，
     * 留出空档是为了将来插入中间态（比如「待发货」）时不用重排既有值。
     * 所以任何地方都别写 {@code status < 30} 这种范围判断，只能逐个比。
     */
    public static final int ORDER_STATUS_UNPAID = 0;
    public static final int ORDER_STATUS_PENDING = 10;
    public static final int ORDER_STATUS_FULFILLING = 20;
    public static final int ORDER_STATUS_FINISHED = 30;
    public static final int ORDER_STATUS_CANCELLED = 40;
    public static final int ORDER_STATUS_REFUNDED = 50;
    public static final int ORDER_STATUS_FAILED = 60;

    // ------------------------------------------------------------------ 订单来源

    public static final String ORDER_SOURCE_NORMAL = "NORMAL";
    /** 限时抢购场次。秒杀模块还没做，这个值先留着 —— DDL 已经写进注释了 */
    public static final String ORDER_SOURCE_FLASH_SALE = "FLASH_SALE";

    /**
     * 排行榜默认取多少条。榜单是给人看的，前十已经能回答「哪些商品最受欢迎」；
     * 再长运营也不会逐条看，反而把页面撑得很高。
     */
    public static final int RANK_TOP_N = 10;

    public static final int MAX_RANK_TOP_N = 50;

    // ------------------------------------------------------------------ 限兑周期

    /** 取值对齐 t_promotion_config.limit_period，别新造一套 */
    public static final String LIMIT_PERIOD_LIFETIME = "LIFETIME";
    public static final String LIMIT_PERIOD_DAILY = "DAILY";
    public static final String LIMIT_PERIOD_WEEKLY = "WEEKLY";
    public static final String LIMIT_PERIOD_MONTHLY = "MONTHLY";

    // ------------------------------------------------------------------ 上架有效期哨兵

    /**
     * start_time / end_time 是 NOT NULL 带哨兵默认值的，<b>不是可空列</b>。
     *
     * <p>可空的话查询要写 {@code (start_time IS NULL OR start_time <= now())}，
     * 这个 OR 走不了索引 —— DDL 里专门解释过。所以运营留空时由服务端填哨兵，
     * 而不是把 null 写进去。
     */
    public static final LocalDateTime SHELF_START_SENTINEL = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
    public static final LocalDateTime SHELF_END_SENTINEL = LocalDateTime.of(2099, 12, 31, 23, 59, 59);

    // ------------------------------------------------------------------ 文件引用

    /**
     * 商品的图片引用登记到 {@code t_file_relation} 用的业务类型。
     *
     * <p><b>拆成两个 bizType 是有意的</b>，因为 {@code FileAssetService.confirm} 是
     * 「按 bizType+bizId 先清后建、顺序即 sort」—— 一个 bizType 只能表达一个有序集合。
     * 而商品有两类引用，诉求不同：
     * <ul>
     *   <li>{@link #BIZ_TYPE_BANNER}：轮播图，<b>顺序有业务含义且要读回来</b>（C 端按 sort 播）</li>
     *   <li>{@link #BIZ_TYPE}：封面 + 各 SKU 图 + 富文本内嵌图，只为登记引用，顺序无意义</li>
     * </ul>
     * 两组互不重叠，并集才是这个商品的完整引用集 —— 删除商品时<b>两个都要 release</b>，
     * 漏一个，那些图就会被孤儿清理任务当垃圾删掉。
     */
    public static final String BIZ_TYPE = "MALL_COMMODITY";

    /** 轮播图专用，见 {@link #BIZ_TYPE} 的说明 */
    public static final String BIZ_TYPE_BANNER = "MALL_COMMODITY_BANNER";

    /** 商品图上传落的素材分类编码（与素材库隔离，见 mall.sql「关于商品图片」一节） */
    public static final String FILE_CATEGORY_CODE = "MALL_COMMODITY";

    // ------------------------------------------------------------------ 其它

    /**
     * 图文详情长度上限。{@code mediumtext} 能装 16MB，但那不该是业务上限 ——
     * 商品详情写到 200KB 基本只有一种可能：有人把 base64 图片粘进来了。
     * 与 {@code ActivityDisplayService} 同口径。
     */
    public static final int MAX_DETAIL_CONTENT_LENGTH = 200_000;

    /** 单个商品的 SKU 数量上限。规格组合爆炸时（3 属性 × 各 10 值）表单本身就不可用了 */
    public static final int MAX_SKU_COUNT = 100;
}
