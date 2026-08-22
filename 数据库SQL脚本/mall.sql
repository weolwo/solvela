-- ℹ️ 本文件的 7 张表<b>尚未部署到任何数据库</b>，所以此刻<b>它就是权威定义</b>。
--    一旦部署进库并重新导出 mysql/schema-baseline.sql，权威就移交给基线，
--    本文件降级为「带设计注释的可读视图」，与其它分域文件一致。
--
-- 保留它是为了那些<b>解释「为什么这么设计」的注释</b> —— 基线是机器导出的，只有结构没有理由。
-- 计划：等各模块开发完工后，把设计注释搬进 docs/营销中台-会话交接文档.md，
--       然后删掉本文件，只留基线。
--
-- 🔴 在那之前，改表结构必须<b>同时</b>改基线和本文件，并跑一次漂移检查：
--       cd 数据库SQL脚本/tools && java -cp <mysql-connector.jar> CheckModuleDrift.java
--    2026-08-22 首次跑这个检查时，分域文件已经漂了 11 张表 —— 其中
--    t_task_record 缺 version、t_task_template 缺 status 是<b>很久以前</b>就漂的，
--    一直没人发现。靠纪律维护两份定义是不成立的，所以才有这个检查。
--
-- ============================================================================
-- 积分商城 DDL
-- ============================================================================
--
-- 定位：**积分兑换**，不是电商。刻意不做的东西（想加之前先回来看这一段）：
--   · 购物车 / 多商品合单  —— 一单一 SKU，订单表因此不需要拆「订单-订单项」两级
--   · 优惠券叠加 / 满减 / 运费模板 / 分销 / 评价 / 售后工单
--   · 会员等级差异化定价  —— 本系统<b>根本没有会员等级</b>（全库 grep 无
--     member_level / vip_level），初版 DDL 里的 vip_rules 是空转字段，已删
--
-- 与既有中台的关系（这决定了很多字段为什么长这样）：
--   · 会员标识是 `member_id bigint`（10位数字，见 member.sql）。
--     单据类表额外冗余一列 member_name 作<b>展示快照</b>（不建索引），
--     后台看订单不用 join 会员表就能认出是谁；状态类表只留 member_id。
--   · 扣积分走 t_member_wallet + t_member_asset_transaction。后者有
--     UNIQUE(biz_ref_id, asset_type)，下单时把 biz_ref_id 传 order_no，
--     **重复扣款天然幂等，不要另造去重表**
--   · 履约复用 sa/ledger/handler 那套：实物落 t_physical_delivery，
--     券落 t_member_coupon。所以 commodity_type 直接对齐 PrizeTypeEnum
--   · 🔴 **商城不走 t_proposal_record**。提案带审批/预算/风控，那是「发钱的闸门」；
--     商城是用户花自己的积分，走审批没道理。但履约要走既有链路，见 §5 的对接说明
--
-- 遵循的工程铁律（见 docs/营销中台-会话交接文档.md §0）：
--   铁律 3  状态/类型一律具名常量，DDL 注释即字典的唯一真源
--   铁律 8  对外标识用 10 位大写字母+数字业务编码，唯一真源 SmartCodeUtil
--   铁律 9  create_time / update_time 只认数据库时钟，代码里不许填；
--           实体上**不要**加 @TableField(fill = ...)，加了会把 null 显式写进去
--
-- 命名：改成 t_mall_* 前缀，对齐项目既有的 t_task_* / t_lottery_* / t_prize_*
--   分域命名法。原 t_commodity_* 在加进订单表后会出现 t_commodity_order 这种
--   读不通的名字。要退回原名：sed -i 's/t_mall_commodity/t_commodity/g' 即可。
-- ============================================================================


-- ============================================================================
-- 关于商品图片：为什么仍然走 t_file，但要跟素材库隔离
-- ============================================================================
--
-- 提出的顾虑是真的：**商品图又多又杂，每个商品都不一样，倒进素材库会把它冲垮。**
-- 素材库的定位是「运营上传活动素材，按分类和标签**检索复用**」（文件模块文档 §1.1），
-- 是个策展空间。而商品图复用率接近零 —— 1000 个商品 × (1主图 + 8轮播 + N个SKU图)
-- 一万多张一次性图片涌进去，运营再想找「双十一主视觉」，翻出来的全是 T 恤白底图。
--
-- 但**解法不是让商城自己建表存 URL**，那要付出三笔代价：
--
--   ① 🔴 删除守卫会失明（最严重，而且是静默的）。
--      FileAssetService:479 的守卫是查 t_file_relation 数引用数的：
--        throw new BusinessException("该文件正被 " + references.size() + " 处业务引用，不能删除");
--      商城不写这张表 → 守卫查不到商城的引用 → 运营在素材库里删掉一张图，
--      守卫说「没人引用」，删除成功，**商城全站那张图当场变死链**。
--      而 <img> 加载失败是静默的（素材库缩略图就是这么坏了半年没人发现，见交接文档「六条必知」#1）。
--      同理 7 天孤儿清理任务也会把商城的图当垃圾清掉。
--
--   ② 等于把文件模块重写一遍：storage_kind（本地→S3 切换后历史文件仍可读）、
--      MIME 嗅探、尺寸校验、缩略图、TEMP/CONFIRMED 生命周期、storageKey 永不覆盖规则。
--      商品图一样需要缩略图 —— 后台商品列表 50 张原图不做缩略图直接卡死。
--
--   ③ 富文本详情里的内嵌图，引用登记已经有现成解法：
--      RichTextImageExtractor.extractImageSources() + fileAssetService.resolveFileIds()，
--      ActivityDisplayService:131 已经在用。自建一套要重写并重新踩一遍坑。
--
-- 而文件模块文档 §4.3 自己立的原则正好覆盖这个场景：
--     「同构的关系可以通用，异构的属性不要通用」
--   —— 它举的反例里就有商品（"活动要规则、**商品要参数**、公告要落款"）。
--   也就是说：**商品的展示属性不该塞进通用表**（所以商城有自己的主表和 SKU 表，对的）；
--   但「文件被某个业务对象引用」这件事在商城和活动里是同构的，通用没问题。
--
-- 【实际做法：分类隔离，而不是另起炉灶】
--   t_file 本来就有 category_id。给商城开专属分类，把商品图**挡在素材库的浏览视图之外**：
--
--   1. 新增文件分类 MALL_COMMODITY（见下方 INSERT）。铁律：代码按 category_code 引用，
--      绝不引用自增 ID —— dev 上是 5、prod 上可能是 9，代码是同一份。
--   2. 素材库列表页的查询固定排除策展外的分类（`category_tag <> '商城'`）。
--      顺带治好一个次生问题：素材库的标签搜索是 `tags LIKE '%,x,%'` 全表扫，
--      文档自己说「几万行几十毫秒，可接受」—— 一万多张商品图会把它推向十万级。
--      加了分类过滤后走 idx_category_time，反而比现在快。
--   3. 商品图的上传入口是**商品表单里的上传组件**，不是素材选择器。
--      运营在商品表单里直接传，落 MALL_COMMODITY 分类，永远不出现在素材库列表里。
--      两边在运营视角下彻底互不干扰。
--   4. 但 t_file_relation 照常写 → 删除守卫、孤儿清理、存储介质切换全部照常生效。
--
--   净效果：运营看到的是两个独立的图片空间，而底层只有一套存储、一套生命周期、一套安全网。
-- ----------------------------------------------------------------------------

-- 商城专属文件分类。category_tag='商城' 是素材库列表的排除依据
INSERT INTO `t_file_category` (`category_code`, `category_name`, `category_tag`, `sort`)
VALUES ('MALL_COMMODITY', '商城商品图', '商城', 100)
ON DUPLICATE KEY UPDATE `category_name` = VALUES(`category_name`);

-- 🔴 内置分类保护要把它加进去，否则运营删掉这个分类，历史商品图全成孤儿：
--    FileCategoryService 的 SYSTEM_CODES 补上 "MALL_COMMODITY"
--    （Set.of("COMMON","NOTICE","HELP_DOC","FEEDBACK") → 加一项）
-- ============================================================================


-- ============================================================================
-- 1. t_mall_category  商品分类
-- ============================================================================
--
-- 刻意只有自增 id、没有 category_code：分类是纯运营数据，代码不引用它
-- （素材分类那条「一律按 categoryCode 引用」的教训针对的是**被代码硬编码引用**的分类，
--  这里不适用）。C 端宫格导航按 id 取即可。
--
-- parent_id 保留但**业务上限死两级**，在 Service 里卡（parent 的 parent 必须为 0）。
-- 不卡的话运营能建出五级菜单，C 端宫格导航根本渲染不了。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_mall_category`;
CREATE TABLE `t_mall_category`
(
    `id`            bigint      NOT NULL AUTO_INCREMENT COMMENT 'id',
    `parent_id`     bigint      NOT NULL DEFAULT 0 COMMENT '父级id：0-顶级分类。业务上限死两级',

    `category_name` varchar(50) NOT NULL COMMENT '分类名称：如 数码3C / 虚拟权益',
    `icon_file_id`  bigint               DEFAULT NULL COMMENT '分类图标 file_id（C端宫格导航用）',
    `sort`          int         NOT NULL DEFAULT 0 COMMENT '排序：从小到大',
    `status`        tinyint     NOT NULL DEFAULT 1 COMMENT '状态：0-禁用, 1-启用',

    `create_by`     varchar(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`   datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     varchar(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`   datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 同级不许重名。不加的话运营手滑建两个「数码3C」，C 端出现两个一模一样的宫格
    UNIQUE KEY `uk_mall_cat_parent_name` (`parent_id`, `category_name`),
    KEY `idx_mall_cat_status_sort` (`status`, `sort`)
) COMMENT ='商城-商品分类';


-- ============================================================================
-- 2. t_mall_commodity  商品主表
-- ============================================================================
--
-- 【与初版的关键差异】
--   ① commodity_type 由 tinyint(1-实物/2-虚拟) 改成 varchar 对齐 PrizeTypeEnum。
--      收益很实在：sa/ledger/handler 下的履约 handler 本就按 PrizeTypeEnum 分派，
--      类型一对齐，发货/发券基本不用写新代码。
--   ② 新增 asset_ref。初版把「虚拟商品发卡密」写在类型注释里，**却没有任何字段
--      能存"发哪张券"** —— 虚拟商品在 DDL 层面根本无法落地。
--   ③ 图片存 file_id 而非 URL，走文件模块。**但商品图与素材库做分类隔离**，
--      理由与做法见文件顶部的「关于商品图片」一节 —— 这是被专门讨论过的决定。
--   ④ limit_per_user 拆成 limit_period + limit_count，复用 t_promotion_config
--      已有的周期字典，不新造一套。
--   ⑤ start_time / end_time 改 NOT NULL 带哨兵默认值。可空的话查询要写
--      `(start_time IS NULL OR start_time <= now())`，**这个 OR 走不了索引**。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_mall_commodity`;
CREATE TABLE `t_mall_commodity`
(
    `id`              bigint         NOT NULL AUTO_INCREMENT COMMENT 'id',
    -- 铁律 8：对外唯一标识。自增 id 各环境不同，一旦被 C 端楼层配置/履约单引用就锁死了迁移
    `commodity_code`  varchar(32)    NOT NULL COMMENT '商品编码：10位大写字母+数字，全局唯一，创建后不可改',
    `category_id`     bigint         NOT NULL COMMENT '分类id',

    -- ---------- 商品性质：决定走哪条履约通道 ----------
    -- 取值对齐 sa.enums.PrizeTypeEnum，不含 SCORE（积分换积分无意义）
    `commodity_type`  varchar(32)    NOT NULL DEFAULT 'PHYSICAL' COMMENT '商品类型：PHYSICAL-实物(走t_physical_delivery), COUPON-优惠券(走t_member_coupon), BALANCE-现金/红包(走钱包入账)',
    -- PHYSICAL 为空；COUPON 存券模编码；BALANCE 存面额来源标识
    `asset_ref`       varchar(64)             DEFAULT NULL COMMENT '资产引用：COUPON 存券模编码，PHYSICAL 为空。语义对齐 t_proposal_record.asset_ref',

    -- ---------- 展示信息 ----------
    `commodity_name`  varchar(128)   NOT NULL COMMENT '商品名称',
    `commodity_intro` varchar(255)            DEFAULT NULL COMMENT '副标题/一句话卖点',
    `cover_file_id`   bigint         NOT NULL COMMENT '封面主图 file_id（建议 800x800）',
    -- 轮播图不在这里，复用 t_file_relation(biz_type='MALL_COMMODITY', biz_id=本表id, sort)
    -- —— 那张表的 sort 列注释原文就是「附件顺序，轮播图必需」，专门为此存在，不要重复造图册表
    `detail_content`  mediumtext COMMENT '图文详情，富文本HTML。禁止 base64 内联图片（对齐 t_activity_display.rule_content）',
    `exchange_notice` varchar(1024)           DEFAULT NULL COMMENT '兑换须知：券的核销说明、实物的发货时效等。C端下单页固定展示',

    -- ---------- 定价 ----------
    -- 主表价是「基准价」，SKU 可覆盖。无规格商品只有一个 SKU，两边一致
    `pay_type`        tinyint        NOT NULL DEFAULT 1 COMMENT '支付方式：1-纯积分, 2-积分+现金',
    `original_price`  decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '划线原价：仅前端展示「价值￥199」，纯积分商品可留 0',
    `points_price`    int            NOT NULL DEFAULT 0 COMMENT '基准兑换积分',
    `cash_price`      decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '基准兑换现金：pay_type=1 时恒为 0',

    -- ---------- 限兑 ----------
    -- 取值对齐 t_promotion_config.limit_period 的字典，别新造一套
    `limit_period`    varchar(32)    NOT NULL DEFAULT 'LIFETIME' COMMENT '限兑周期：LIFETIME-终身, DAILY-每日, WEEKLY-每周, MONTHLY-每月',
    `limit_count`     int            NOT NULL DEFAULT 0 COMMENT '周期内单会员限兑件数：0-不限制',

    -- ---------- 上架控制 ----------
    -- 哨兵默认值而非 NULL：让 `where start_time <= now() and end_time >= now()` 能走索引
    -- 🔴 这两列是**商品的上架有效期**，不是秒杀场次。初版注释写的「用作秒杀/活动」是个陷阱：
    --    一个商品会有双11一场、双12一场、每天10点一场，两个 datetime 只能表达一场。
    --    秒杀归秒杀场次表管，见文件末尾「关于后续的秒杀模块」。
    `start_time`      datetime       NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '上架开始时间：默认值代表不限。不是秒杀场次',
    `end_time`        datetime       NOT NULL DEFAULT '2099-12-31 23:59:59' COMMENT '上架结束时间：默认值代表不限。不是秒杀场次',
    `status`          tinyint        NOT NULL DEFAULT 2 COMMENT '状态：0-下架, 1-上架, 2-草稿。新建默认落草稿',
    `is_home`         tinyint        NOT NULL DEFAULT 0 COMMENT '是否首页推荐：0-否, 1-是',
    `sort`            int            NOT NULL DEFAULT 0 COMMENT '排序权重：从小到大',

    -- ---------- 冗余统计 ----------
    -- 不能用 total_stock 反推销量：运营会补货，一补 total_stock 就变了
    `sold_count`      int            NOT NULL DEFAULT 0 COMMENT '累计已兑件数（各SKU之和的冗余，用于列表按热销排序）',

    `create_by`       varchar(64)             DEFAULT NULL COMMENT '创建人',
    `create_time`     datetime                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       varchar(64)             DEFAULT NULL COMMENT '更新人',
    `update_time`     datetime                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mall_cmd_code` (`commodity_code`),
    -- C 端分类页是 `where category_id=? and status=1 order by sort`。
    -- 初版的 idx_status_sort 缺了 category_id 打头，分类页用不上
    KEY `idx_mall_cmd_cat_status_sort` (`category_id`, `status`, `sort`),
    KEY `idx_mall_cmd_home` (`is_home`, `status`, `sort`)
) COMMENT ='商城-商品主表';


-- ============================================================================
-- 3. t_mall_sku  SKU 与库存
-- ============================================================================
--
-- 【库存模型：total 恒定，locked 与 sold 分别累加】
--   available_stock = total_stock - locked_stock - sold_count（虚拟列，MySQL 自动算）
--
--   初版的模型是「卖出后 total_stock 递减」，问题是运营再也看不出原始投放量
--   —— 界面上「总库存 63」到底是投了 63 件，还是投了 100 件卖了 37 件？
--   本版 total_stock 恒等于运营设定值，补货 = 直接改 total_stock，
--   一眼看清「投 100 / 售 37 / 锁 2 / 余 61」。
--
-- 【🔴 删掉了 version 乐观锁】
--   初版 locked_stock（行内条件扣减）和 version（CAS）两套防超卖机制并存，
--   说明模型没定下来。条件扣减本身就已经防住超卖了：
--
--     -- ① 下单锁定
--     UPDATE t_mall_sku SET locked_stock = locked_stock + #{qty}
--      WHERE id = #{id} AND sku_status = 1
--        AND total_stock - locked_stock - sold_count >= #{qty};
--     -- ② 履约成功：锁定转已售
--     UPDATE t_mall_sku SET locked_stock = locked_stock - #{qty}, sold_count = sold_count + #{qty}
--      WHERE id = #{id} AND locked_stock >= #{qty};
--     -- ③ 取消 / 超时释放
--     UPDATE t_mall_sku SET locked_stock = locked_stock - #{qty}
--      WHERE id = #{id} AND locked_stock >= #{qty};
--
--   三条都是单行原子 UPDATE，affected rows = 0 即失败。version 只服务于
--   「查出来→改→写回去」那种写法，在这里纯属让高并发白白 CAS 失败重试。
--
-- 【只有 pay_type=2 才真的需要「锁」】
--   纯积分兑换是同步扣的，①②可以在一个事务里连做。
--   积分+现金要等第三方支付回调，中间悬着，所以订单表有 expire_time
--   和配套的超时释放 job（见 §4）。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_mall_sku`;
CREATE TABLE `t_mall_sku`
(
    `id`               bigint         NOT NULL AUTO_INCREMENT COMMENT 'id',
    `commodity_id`     bigint         NOT NULL COMMENT '关联 t_mall_commodity.id',
    -- 铁律 8。t_proposal_record.asset_ref 的注释原文就是「资产引用：券模/SKU」——
    -- 履约链路本就按编码引用 SKU，只有自增 id 是接不进去的
    `sku_code`         varchar(32)    NOT NULL COMMENT 'SKU编码：10位大写字母+数字，全局唯一',

    -- 无规格商品也必须有一行，sku_attrs 填 {}
    `sku_attrs`        json           NOT NULL COMMENT '规格组合：{"颜色":"星空灰","尺码":"XL"}。无规格商品填 {}',
    `sku_cover_file_id` bigint                 DEFAULT NULL COMMENT '该规格专属图 file_id：C端切换规格时换主图，为空则用商品封面',

    -- 为空则继承主表基准价。刻意允许 NULL 而非默认 0：0 是「免费兑换」的合法取值，
    -- 用 0 当"未设置"就分不清「没填」和「真免费」了
    `sku_points_price` int                     DEFAULT NULL COMMENT '本规格所需积分：为空则继承 t_mall_commodity.points_price',
    `sku_cash_price`   decimal(10, 2)          DEFAULT NULL COMMENT '本规格所需现金：为空则继承 t_mall_commodity.cash_price',

    -- ---------- 库存 ----------
    `total_stock`      int            NOT NULL DEFAULT 0 COMMENT '总库存：运营投放量，恒定不变，补货改这里',
    `locked_stock`     int            NOT NULL DEFAULT 0 COMMENT '锁定库存：已下单未履约（仅 pay_type=2 会悬挂）',
    `sold_count`       int            NOT NULL DEFAULT 0 COMMENT '已售数量：履约成功累加',
    `available_stock`  int GENERATED ALWAYS AS (`total_stock` - `locked_stock` - `sold_count`) VIRTUAL COMMENT '可用库存（虚拟列，勿写入）',

    -- 单 SKU 上下架：某个颜色停售不该整个商品下架
    `sku_status`       tinyint        NOT NULL DEFAULT 1 COMMENT '状态：0-停用, 1-启用',
    `sort`             int            NOT NULL DEFAULT 0 COMMENT '排序',

    `create_by`        varchar(64)             DEFAULT NULL COMMENT '创建人',
    `create_time`      datetime                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        varchar(64)             DEFAULT NULL COMMENT '更新人',
    `update_time`      datetime                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- JSON 列做不了唯一索引，所以规格组合的去重靠 sku_code 唯一 + Service 里比对 attrs。
    -- 不做的话，表单重复提交会生成一堆规格完全相同的 SKU
    UNIQUE KEY `uk_mall_sku_code` (`sku_code`),
    KEY `idx_mall_sku_cmd` (`commodity_id`, `sku_status`, `sort`)
) COMMENT ='商城-SKU与库存';


-- ============================================================================
-- 4. t_mall_order  兑换订单
-- ============================================================================
--
-- 🔴 **初版整个交易域是空的**：只有商品怎么陈列，没有一行记录「谁兑了什么」。
--    后果是 limit_per_user 无处可算、C端「我的兑换」查不出来、退积分无凭据。
--
-- 一单一 SKU，不做「订单-订单项」两级。积分商城没有购物车，拆两级是纯负担。
--
-- 【所有商品信息都是快照，不是外键读取】
--   运营下周把「T恤」改名成「限定T恤」、把 5000 分调成 8000 分，
--   历史订单必须还长原来的样子。靠 join 商品表拿名字和价格，改一次价历史全乱。
--
-- 【幂等】
--   扣积分写 t_member_asset_transaction 时 biz_ref_id 传 order_no，
--   那张表上的 UNIQUE(biz_ref_id, asset_type) 直接挡住重复扣款，不用另做。
--
-- 【与既有履约链路的对接 —— 有一处必须改动上游】
--   t_physical_delivery 的唯一键是 (proposal_id, source_type)，且
--   proposal_id 是 bigint NOT NULL。商城订单没有提案 ID，**插不进去**。
--   两条路，推荐后者：
--     ✗ 商城也造一条提案 —— promotion_config_id 也是 NOT NULL，还得硬编一条假的优惠配置
--     ✓ 把 t_physical_delivery.proposal_id 泛化成 source_biz_id varchar(64)，
--       唯一键改 (source_biz_id, source_type)。语义反而更正：那张表本就有
--       source_type 字段，本就是按「只认单号、不认上游业务」设计的。
--       迁移：ALTER 加列 → UPDATE source_biz_id = CAST(proposal_id AS CHAR) → 换索引 → 删旧列。
--   然后商城以 source_type='MALL' 写入即可，运营的发货台/物流导入完全复用。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_mall_order`;
CREATE TABLE `t_mall_order`
(
    `id`               bigint         NOT NULL AUTO_INCREMENT COMMENT 'id',
    `order_no`         varchar(32)    NOT NULL COMMENT '订单号：服务端生成，对外唯一标识，同时作为扣积分的幂等键',
    -- 关联键。全局发号器产生、全局唯一、永不可变，见 member.sql
    `member_id`        bigint         NOT NULL COMMENT '会员号：关联键',
    -- 🔴 展示快照，<b>不是</b>关联键，刻意<b>不建索引</b>。
    --    订单是「单据」——写完就不再改的历史记录，所以这里记的应当是<b>下单当时</b>那个账号，
    --    而不是这人现在叫什么（微信号一年可改一次）。审计要回答的是「当时是谁」。
    --    这和本表已有的 commodity_name / sku_attrs 快照是同一个模式。
    --    不建索引也是一道防线：谁写了 `WHERE member_name=?` 会立刻表现为慢查询被发现；
    --    一旦给它建了索引，关联键就会悄悄退回 member_name，改名断链的问题原样复活。
    `member_name`      varchar(32)             DEFAULT NULL COMMENT '下单时的会员账号【展示快照，非关联键，不要用于查询】',

    -- ---------- 商品引用 ----------
    `commodity_id`     bigint         NOT NULL COMMENT '商品id',
    `commodity_code`   varchar(32)    NOT NULL COMMENT '商品编码（跨环境稳定的那个）',
    `sku_id`           bigint         NOT NULL COMMENT 'SKUid',
    `sku_code`         varchar(32)    NOT NULL COMMENT 'SKU编码',

    -- ---------- 下单瞬间的快照，之后与商品表脱钩 ----------
    `commodity_type`   varchar(32)    NOT NULL COMMENT '商品类型快照：PHYSICAL / COUPON / BALANCE，履约分派靠它',
    `asset_ref`        varchar(64)             DEFAULT NULL COMMENT '资产引用快照：券模编码等',
    `commodity_name`   varchar(128)   NOT NULL COMMENT '商品名称快照',
    `cover_file_id`    bigint                  DEFAULT NULL COMMENT '封面图快照 file_id',
    `sku_attrs`        json           NOT NULL COMMENT '规格快照',
    `quantity`         int            NOT NULL DEFAULT 1 COMMENT '兑换件数',
    `points_price`     int            NOT NULL DEFAULT 0 COMMENT '单件积分单价快照',
    `cash_price`       decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '单件现金单价快照',
    `pay_points`       int            NOT NULL DEFAULT 0 COMMENT '实付积分合计',
    `pay_cash`         decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '实付现金合计',

    -- ---------- 收货信息（PHYSICAL 才有）----------
    -- 🔴 这里<b>只存地址簿引用，不存收件人姓名/电话/详细地址</b>。
    --    初版把这三列抄了一份进订单表，与 t_physical_delivery 的同名三列完全重叠，代价是：
    --      ① 同一份个人信息存两份 —— 加密要做两遍、脱敏要做两遍、注销清理要清两处，
    --         泄露面平白翻倍；
    --      ② 会不一致 —— 用户打电话说搬家了，运营在发货单上改了地址，
    --         订单上还是老地址。客服看订单和看发货单得到两个答案，且无从判断哪个是真的。
    --
    --    正确的分工：<b>快照的归宿是履约单，不是订单</b>。
    --      · t_physical_delivery 的 receiver_* 三列本来就是可空的，
    --        原注释写着「中奖时未知，由用户后续补填」—— 它天生就支持「先落单、地址后补」，
    --        商城这个场景不用改它一个字。
    --      · 支付成功创建履约单时，把地址从 t_mall_address 读出来写进 t_physical_delivery。
    --        那一刻起地址就冻结了，之后用户怎么改地址簿都不影响已发货的单。
    --      · 待支付期间（仅 pay_type=2）地址还没冻结，靠 address_id 指着地址簿。
    --        用户此时改了地址簿内容，按最新的发 —— 这是符合预期的行为。
    --
    --    ⚠️ address_id 是<b>软引用</b>，不加外键：用户可能在待支付期间删掉这条地址。
    --       支付时必须重查一次，查不到就拦下来让用户重选，不要拿着空地址去建履约单。
    `address_id`       bigint                  DEFAULT NULL COMMENT '收货地址id(软引用t_mall_address)，仅PHYSICAL有值。收件信息快照在t_physical_delivery，不在本表',

    -- ---------- 状态机 ----------
    -- 0 →(支付/直接扣分)→ 10 →(投递履约)→ 20 →(履约回执)→ 30 / 60
    --   ↓超时或用户取消
    --   40（释放库存 + 原路退积分）
    `status`           int            NOT NULL DEFAULT 0 COMMENT '状态：0-待支付, 10-待履约, 20-履约中, 30-已完成, 40-已取消, 50-已退款, 60-履约失败',
    -- 只有 pay_type=2 的订单有值：纯积分是同步扣的，不存在悬挂
    `expire_time`      datetime                DEFAULT NULL COMMENT '待支付超时时间：到点由 job 取消并释放锁定库存。纯积分订单为空',
    `pay_time`         datetime                DEFAULT NULL COMMENT '支付/扣分完成时间',
    `finish_time`      datetime                DEFAULT NULL COMMENT '履约完成时间',
    `cancel_time`      datetime                DEFAULT NULL COMMENT '取消时间',

    -- ---------- 履约回执 ----------
    -- ---------- 订单来源 ----------
    -- 🔴 为后续的秒杀模块预留的**唯一**两个字段，而且它们本身对普通订单也成立。
    --    口径完全对齐项目既有的 t_proposal_record / t_member_coupon / t_physical_delivery
    --    ——「只认单号，不认上游的业务语义」。
    --    晚加会疼的原因：存量订单的来源无从回填，一旦上了秒杀，
    --    「这场秒杀到底卖了多少」「这单该退回哪个场次的库存」「谁在刷」全部答不出来。
    --    而这两列现在加，普通订单填 NORMAL 即可，零成本。
    `source_type`      varchar(32)    NOT NULL DEFAULT 'NORMAL' COMMENT '订单来源：NORMAL-日常兑换, FLASH_SALE-限时抢购场次',
    `source_biz_id`    varchar(64)             DEFAULT NULL COMMENT '来源单号：FLASH_SALE 时存场次编码，NORMAL 为空',

    `fulfill_ref_id`   varchar(64)             DEFAULT NULL COMMENT '履约单引用：发货单id / 券id',
    `fail_reason`      varchar(255)            DEFAULT NULL COMMENT '履约失败原因（status=60 时有值）',
    `remark`           varchar(255)            DEFAULT NULL COMMENT '用户备注 / 运营备注',

    `create_by`        varchar(64)             DEFAULT NULL COMMENT '创建人',
    `create_time`      datetime                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        varchar(64)             DEFAULT NULL COMMENT '更新人',
    `update_time`      datetime                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mall_ord_no` (`order_no`),
    -- C端「我的兑换」
    KEY `idx_mall_ord_member` (`member_id`, `create_time`),
    -- 🔴 超时释放 job 的扫描索引。没有它，job 每分钟全表扫一遍订单表
    KEY `idx_mall_ord_expire` (`status`, `expire_time`),
    -- 运营台按商品查兑换明细 / 对账
    KEY `idx_mall_ord_cmd` (`commodity_id`, `status`, `create_time`),
    -- 秒杀场次对账：一场秒杀卖了多少、成交率多少
    KEY `idx_mall_ord_source` (`source_type`, `source_biz_id`, `status`)
) COMMENT ='商城-兑换订单';


-- ============================================================================
-- 5. t_mall_exchange_limit  限兑计数
-- ============================================================================
--
-- 【为什么不直接 count 订单表】
--   `select count(*) from t_mall_order where member_id=? and commodity_id=?`
--   在并发下有竞态：两个请求同时读到 count=0，双双通过校验。
--   限兑 1 件的爆款商品，这个洞会被刷。
--
-- 【为什么不用分布式锁】
--   Redisson 锁能挡住，但锁的粒度是 member+commodity，热门商品下所有用户的请求
--   会在同一批 key 上排队；且锁超时/Redis 抖动时校验直接裸奔。
--   唯一索引 + 条件 UPDATE 是数据库自己保证的，没有这些问题。
--
-- 【period_key 的取值对齐 t_task_record.period_key 的既有做法】
--   LIFETIME → 'NONE'；DAILY → '20260819'；WEEKLY → '2026W34'；MONTHLY → '202608'
--   秒杀的「每场限购 N 件」也复用这张表，不必加列：period_key 写 'FS#' + 场次编码
--   （3 + 10 = 13 字符，varchar(32) 装得下）。唯一索引天然按场次隔离，
--   日常限兑与秒杀限购互不干扰。
--   🔴 period_key 必须由**数据库时钟**算（铁律 9/10），不要用 JVM 时间，
--      否则跨时区部署时日切点对不上，用户在 00:00~08:00 之间能多兑一次。
--
--   下单：INSERT ... ON DUPLICATE KEY UPDATE
--           used_count = IF(used_count + #{qty} <= #{limitCount}, used_count + #{qty}, used_count);
--         再判 affected rows —— MySQL 对「值没变的 UPDATE」返回 0，正好是超限信号。
--   取消/退款：UPDATE ... SET used_count = used_count - #{qty} WHERE ... AND used_count >= #{qty};
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_mall_exchange_limit`;
CREATE TABLE `t_mall_exchange_limit`
(
    `id`           bigint      NOT NULL AUTO_INCREMENT COMMENT 'id',
    `member_id`    bigint      NOT NULL COMMENT '会员号：关联键',
    `commodity_id` bigint      NOT NULL COMMENT '商品id',
    `period_key`   varchar(32) NOT NULL DEFAULT 'NONE' COMMENT '周期标识：NONE(终身) / 20260819(日) / 2026W34(周) / 202608(月)。取值口径对齐 t_task_record.period_key',
    `used_count`   int         NOT NULL DEFAULT 0 COMMENT '该周期内已兑件数',

    `create_time`  datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 这条唯一索引就是限兑的正确性来源，不是性能优化
    UNIQUE KEY `uk_mall_lmt_mbr_cmd_prd` (`member_id`, `commodity_id`, `period_key`)
) COMMENT ='商城-会员限兑计数';


-- ============================================================================
-- 6. t_mall_address  会员收货地址簿
-- ============================================================================
--
-- 每次兑换都手打一遍地址是最劝退的一步。这张表很轻，但对「好用」的贡献最大。
-- 顺带把 t_physical_delivery 的老问题也解了：抽奖中奖后补填地址那条链路
-- 同样可以从这里带出来，不用用户再打一遍。
--
-- ----------------------------------------------------------------------------
-- 🔴 收件信息是个人信息，必须密文存储 —— 但这里和 t_member.phone 的做法<b>不一样</b>
-- ----------------------------------------------------------------------------
-- 关键区别：<b>这几列永远不需要被查询</b>。
--   t_member.phone 之所以要「密文 + hash」双写，是因为登录要按手机号找人、
--   还要做唯一约束 —— 密文没法查，才不得不再存一列 hash。
--   而地址簿的访问路径只有一条：`WHERE member_id = ?` 取出来展示 / 发货时读出来。
--   没有任何「按收件人姓名查」「按电话查」的场景。
--
--   所以这里<b>只要可逆加密，不需要 hash 列</b>。少一列、少一套盐、少一处要保管的东西。
--   谁要是哪天想给它加 hash，先问清楚是要满足哪个查询 —— 大概率是不需要的。
--
-- 【省市区刻意<b>不</b>加密】
--   「浙江省杭州市西湖区」识别不到任何具体的人，泄露价值极低；
--   而它有真实用途：后台按省份看发货分布、按区域算物流成本、大促前备货。
--   加密了这些统计就全废了（解密全表再聚合是不现实的）。
--   个人信息保护的目标是「不能定位到具体自然人」，不是「所有字段一律变密文」——
--   把不敏感的维度一起加密，只会让系统变笨，安全性并没有提高。
--   真正能定位到人的是 收件人姓名 + 电话 + 门牌号，这三个加密。
--
-- 【长度为什么给这么大】
--   AES-GCM + Base64 后长度约 = ceil((28 + 明文字节数) / 3) * 4：
--     · 3 字中文姓名 (9B)  → 约 52 字符
--     · 11 位手机号 (11B)  → 约 52 字符
--     · 50 字中文地址(150B)→ 约 240 字符
--   所以姓名/电话 varchar(255) 绰绰有余，详细地址给 varchar(512) 留足头寸。
--   ⚠️ 别按明文长度定义列宽 —— 密文塞不下时 MySQL 非严格模式是<b>静默截断</b>，
--      表现是「地址存进去了，读出来解密失败」，而且已经存坏的行救不回来。
--
-- 【脱敏是展示层的事，不要为它存第二份数据】
--   后台列表要显示「张*」「138****8888」「浙江省杭州市西湖区***」，
--   这是解密后在应用层截的，不是再存一列明文脱敏值。存两份就又回到了
--   「同一份个人信息存两处」的老问题。
--
-- 【t_physical_delivery 有同样的三列，也该一起加密】
--   那是已部署的表，属于独立的一次改造，但口径要统一 —— 否则订单侧加密了、
--   发货单侧还是明文，等于没加。见「附」。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_mall_address`;
CREATE TABLE `t_mall_address`
(
    `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT 'id',
    `member_id`      bigint       NOT NULL COMMENT '会员号：关联键',

    -- 密文三件套。可逆加密(AES-GCM/SM4)，密钥走配置中心，与库分开保管
    `receiver_name`  varchar(255) NOT NULL COMMENT '收件人姓名【密文】',
    `receiver_phone` varchar(255) NOT NULL COMMENT '收件人电话【密文】',
    `detail_address` varchar(512) NOT NULL COMMENT '详细门牌地址【密文】',
    -- 省市区不加密：识别不到具体个人，但后台发货分布/物流成本统计要用
    `province`       varchar(32)           DEFAULT NULL COMMENT '省【明文，可统计】',
    `city`           varchar(32)           DEFAULT NULL COMMENT '市【明文，可统计】',
    `district`       varchar(32)           DEFAULT NULL COMMENT '区/县【明文，可统计】',

    `is_default`     tinyint      NOT NULL DEFAULT 0 COMMENT '是否默认地址：0-否, 1-是。设默认时先把该会员其余行置0',

    `create_by`      varchar(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`    datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      varchar(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`    datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_mall_addr_member` (`member_id`, `is_default`)
) COMMENT ='商城-会员收货地址簿';


-- ============================================================================
-- 7. t_mall_favorite  商品收藏
-- ============================================================================
--
-- 【为什么积分商城的收藏比电商的收藏更值钱】
--   电商收藏 = 「以后有钱再买」，是个弱意图。
--   积分商城收藏 = 「我正在为这个攒分」—— 用户看中 8000 分的商品、手上只有 3000 分，
--   收藏是他<b>唯一</b>能表达意图的动作，而且他会为此持续回来做任务攒分。
--   这是积分体系的核心正反馈回路，不是可有可无的功能。
--
-- 【运营价值：这是兑换<b>之前</b>的需求信号】
--   销量只能告诉你「什么卖掉了」，收藏能告诉你「什么想要但没兑」。
--   收藏高 + 兑换低 = 定价太高或库存不足，这个信号比销量早暴露一个周期。
--   报表：按 commodity_id 聚合收藏数，左连订单数，差值排序即可（走 idx_mall_fav_cmd）。
--
-- 【商品粒度，不是 SKU 粒度】
--   用户收藏的是「那件 T 恤」，不是「星空黑 XL」。按 SKU 收藏会让收藏列表
--   出现同一商品的好几行，而用户根本记不得自己当初点的是哪个规格。
--
-- 【🔴 取消收藏必须物理删，不能软删】
--   软删 + UNIQUE(member_id, commodity_id) 会在「收藏 → 取消 → 再收藏」时
--   撞上那条被软删的行报唯一键冲突。收藏没有任何历史价值，删了就是删了。
--   （本模块其它表也都没有 deleted_flag，口径一致。）
--
-- 【刻意不在商品表冗余 collect_count】
--   冗余就要在并发下维护 +1/-1，且必然漂移。收藏数只有两个消费场景：
--   C端详情页显示、后台报表排序 —— 前者按 commodity_id 走索引 count 足够快，
--   后者是后台查询，慢一点无所谓。真扛不住再加，那时用条件 UPDATE 补。
--
-- 【想让收藏真正产生价值，还需要一个本项目暂时没有的东西】
--   「你收藏的 XX 补货了 / 降到 5000 分了 / 明天下架」这类触达，是收藏功能
--   80% 的价值来源。目前系统里没有 C 端消息触达通道（t_message 是后台站内信）。
--   收藏表可以先建、先收数据，触达能力另立项 —— 但要清楚：<b>不做触达的话，
--   收藏就只是一个用户自己会忘记的列表。</b>
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_mall_favorite`;
CREATE TABLE `t_mall_favorite`
(
    `id`           bigint      NOT NULL AUTO_INCREMENT COMMENT 'id',
    `member_id`    bigint      NOT NULL COMMENT '会员号：关联键',
    `commodity_id` bigint      NOT NULL COMMENT '商品id（商品粒度，不是SKU粒度）',

    -- 本表的行只有插入和删除，不存在更新。update_time 仍按铁律 9 写全，
    -- 保持全库自查脚本口径一致
    `create_time`  datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    `update_time`  datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 防重复收藏。前端连点两次是常态，靠这条索引兜住
    UNIQUE KEY `uk_mall_fav_mbr_cmd` (`member_id`, `commodity_id`),
    -- C端「我的收藏」按时间倒序
    KEY `idx_mall_fav_mbr_time` (`member_id`, `create_time`),
    -- 详情页收藏数 + 后台需求信号报表
    KEY `idx_mall_fav_cmd` (`commodity_id`)
) COMMENT ='商城-商品收藏';


-- ============================================================================
-- 关于 is_hot：不加。「热门」应该是算出来的，不是标出来的
-- ============================================================================
--
-- 商品表已经有 is_home（首页推荐）+ sort（排序权重）+ sold_count（累计已兑）。
-- 再加 is_hot 会同时踩三个坑：
--
--   ① <b>标志位增殖</b>。运营的第一个问题一定是「首页推荐和热门有什么区别」，
--      而这个问题没有标准答案 —— 结果就是两个开关被随手勾选，
--      C 端两个位置展示同一批商品。接下来还会要 is_new、is_limited、is_ending，
--      每加一个楼层就加一个布尔列，第四个楼层上线时这套就崩了。
--
--   ② <b>会和真实数据打架</b>。手工标的「热门」下面挂着 0 兑换量，C 端很难看，
--      而且没有任何机制提醒运营去撤掉标记 —— 标记只会越攒越多、越来越不准。
--
--   ③ <b>它本来就能算出来</b>。sold_count 已经在表上了：
--        热门榜 = ORDER BY sold_count DESC
--        近期热门 = 按 t_mall_order 近 7 天分组计数（走 idx_mall_ord_cmd）
--      永远不会和真实数据打架，零字段成本。
--
-- 【冷启动怎么办】——这是唯一支持手工标记的理由，但它不需要新字段：
--   新商城 sold_count 全是 0，算不出热门。这段时期直接用 sort 排序即可，
--   sort 本来就是给运营手工控顺序用的。撑过冷启动后自然切到 sold_count。
--
-- 【如果运营确实需要多个可手工干预的楼层】
--   那就<b>一次性</b>上运营位表，不要再加布尔列：
--     t_mall_showcase(position_code, commodity_id, sort, start_time, end_time)
--     position_code: HOME / HOT / NEW / ENDING ...
--   一个商品可以同时上多个位，位可以带档期，新增楼层不用改表结构。
--   届时 is_home 应该被它取代并从商品表移除 —— is_home 其实就是这个问题的早期形态。
--   但按「不能像电商那么复杂」的定位，<b>现在还不到那一步</b>。
-- ============================================================================


-- ============================================================================
-- 附：落地时必须一起做的三件事（DDL 之外，漏了会静默出问题）
-- ============================================================================
--
-- ① t_physical_delivery 的 proposal_id 泛化成 source_biz_id，见 §4 的说明。
--    不改的话实物商品**根本落不了发货单**。
--    🔴 同一次改造里把它的收件三列一起加密，口径对齐 t_mall_address：
--         receiver_name  varchar(64)  → varchar(255) 密文
--         receiver_phone varchar(32)  → varchar(255) 密文
--         receiver_address varchar(255) → varchar(512) 密文
--    只加密地址簿、不加密发货单等于没加密 —— 收件信息最终<b>都会落到发货单上</b>，
--    而发货单才是运营天天打开、还要导出给物流商的那张表，暴露面比地址簿大得多。
--    存量明文行要用密钥跑一次批量加密迁移，迁移脚本自己也要走配置里的密钥，
--    别在脚本里硬编码。
--
-- ② 超时释放 job：挂到既有的 t_smart_job 上，扫
--      `where status = 0 and expire_time <= now()`（走 idx_mall_ord_expire）
--    对每单：取消订单 → 释放 locked_stock → 回滚 t_mall_exchange_limit.used_count。
--    🔴 三步要在一个事务里，且**必须拆到独立 Bean**（铁律 11：@Transactional
--       被同类自调用会静默失效，本项目已连踩两次）。
--
-- ③ 图片引用登记：商品保存时一次性 confirm **本次保存后的完整引用集合**，
--    照抄 ActivityDisplayService.collectReferencedFileIds() 的写法。四个来源都要收齐：
--      · t_mall_commodity.cover_file_id
--      · 轮播图 bannerFileIds
--      · 各 SKU 的 sku_cover_file_id      ← 最容易漏的一处
--      · detail_content 富文本内嵌图（RichTextImageExtractor + resolveFileIds）
--    🔴 confirm 内部是「先清后建」，所以必须传完整集合而不是增量；
--       且**空集合不能提前 return** —— 它的含义是「这个商品现在一张图都不引用了」，
--       恰恰最需要执行解除引用（FileAssetService:517 的注释记着这个实测踩过的坑）。
--    商品删除时调 fileAssetService.releaseRelation("MALL_COMMODITY", commodityId)。
--
-- ④ 实体类上**不要**加 @TableField(fill = FieldFill.*)（铁律 9）。
--    MyBatis-Plus 见到 insert fill 会直接把该列带进 INSERT 且跳过判空，
--    null 会覆盖掉 DDL 的 DEFAULT CURRENT_TIMESTAMP —— 实测让整列 create_time 变 NULL。
--    代码生成器的 Entity.java.vm 曾硬编码这两个注解，生成完记得检查一遍。
--
-- 自查（新建表后跑一次，铁律 9）：
--   SELECT table_name FROM information_schema.columns
--    WHERE table_schema='smart_admin_v3' AND column_name='update_time'
--      AND extra NOT LIKE '%on update%';
-- ============================================================================


-- ============================================================================
-- 关于后续的秒杀模块：为什么本次<b>不</b>往商品表加字段
-- ============================================================================
--
-- 🔴 结论：秒杀不是商品的属性，是「一段时间内对某商品的一次活动」。
--    同一个商品会有双11一场、双12一场、每天10点一场 —— 任何挂在 t_mall_commodity 上的
--    flash_price / flash_start_time / flash_stock 都只能表达**一场**，
--    上线第二场就得推翻重来，而那时表里已经有数据了。
--
-- 更隐蔽的代价是**第二价格源**：只要商品表上出现 flash_price，
--   取价逻辑就会在 C端列表 / 详情 / 下单 / 订单快照 四处各写一遍 fallback，
--   迟早出现「列表显示秒杀价、下单扣日常价」——而且是静默的，用户不会来投诉便宜了。
--   取价必须收敛到唯一入口：resolvePrice(sku, context) —— 日常态读 SKU→商品的继承链，
--   秒杀态读场次覆盖价。这是代码上的接缝，不是列。
--
-- 【本次只做三件低成本、晚做会疼的事】
--   ① t_mall_order 加 source_type + source_biz_id（已加）。
--      唯一真正"晚加会疼"的地方：存量订单的来源无从回填。
--   ② t_mall_exchange_limit 的 period_key 约定 'FS#'+场次编码（已写进注释），不加列。
--   ③ t_mall_commodity.start_time/end_time 的语义钉死为「上架有效期」（已改注释），
--      防止有人把它当秒杀档期用掉。
--
-- 【真做的时候，秒杀是一张场次表，形状大致如此】
--   t_mall_flash_sale
--     flash_sale_code varchar(32)  场次编码，铁律 8。订单的 source_biz_id 存它
--     commodity_id / sku_id        秒杀是 SKU 粒度的（只秒某个颜色很常见）
--     flash_points / flash_cash   本场覆盖价
--     session_stock / session_sold  本场限量与已售（第二道闸门，见下）
--     start_time / end_time         本场档期，NOT NULL
--     limit_per_member              每人限购
--     status                        0-未开始 1-进行中 2-已结束 3-已取消
--     UNIQUE(flash_sale_code)
--     KEY(commodity_id, start_time)
--     -- 同一 SKU 的场次不许时间重叠，在 Service 里校验（彩票期号重叠踩过同类坑）
--
-- 【库存用双闸门，不要给秒杀单独划一份库存】
--   ① 场次闸门：session_sold < session_stock（本场只放 100 件）
--   ② 真实库存：仍然扣 t_mall_sku 的 locked_stock / sold_count
--   划一份独立库存的话，秒杀没卖完的要往回退、退单要判断退给谁，对账口径立刻分裂成两套。
--
-- ⚠️ 秒杀的并发量级和日常兑换<b>完全不是一个东西</b>：
--   本 DDL 的行内条件 UPDATE 扛日常兑换绰绰有余，但开抢瞬间几万请求打同一行
--   会把那一行锁成串行队列。秒杀要在 MySQL 前面加 Redis 预扣减
--   （项目已有 Redisson 4.6.1），MySQL 只作为最终一致的账。
--   这是秒杀模块自己的课题，与本 DDL 无关 —— 但别指望现在这套直接扛秒杀。
--
-- 【要不要挂到 t_activity_config 上】
--   可选，不必须。那套 activity_type(BASIC/DRAW/TASK/LOTTERY) 是**发奖**玩法引擎，
--   带 promotion_config / prize / proposal 一整套预算风控，而秒杀是**消费**，用不上。
--   如果运营希望秒杀出现在活动列表和作战大屏里，给场次表加一个**可空**的
--   activity_code 关联即可，不要反过来把秒杀塞进玩法引擎。
-- ============================================================================
