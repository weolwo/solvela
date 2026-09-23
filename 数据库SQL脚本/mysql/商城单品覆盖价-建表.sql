-- ⚠️ 必须保留这一行，且必须在所有语句之前（连接字符集若是 latin1，中文注释会整片乱码）。
SET NAMES utf8mb4;

-- =====================================================================================
-- 商城单品覆盖价：给指定商品（或指定规格）在某一档上定死一个价
-- 2026-09-23
--
-- 【和等级折扣率的关系】覆盖价【优先】，命中了就不再打折。
--   折扣率是「全场普惠」，覆盖价是「这一件，这一档，就这个数」。
--   一件 10000 分的商品，白金折扣率 9.2 折 = 9200；配了覆盖价 888 就是 888。
--
-- 🔴 【为什么 sku_id 用 0 而不是 NULL 表示「整个商品」】
--   唯一键是 (commodity_id, sku_id, grade_code)。MySQL 的唯一索引【不约束 NULL】——
--   两行 (3, NULL, 4) 可以同时存在，于是「整个商品对钻石的价」会有两条，
--   而查出来是哪条取决于存储顺序。不报错、不冲突，只是价格随机。
--   用 0 当哨兵，唯一键才真的唯一。
--
-- ⚠️ 【解析顺序，和现有价格模型同一个形状】
--   规格覆盖价 > 商品覆盖价 > 挂牌价 × 等级折扣率
--   前两级的关系刻意抄 t_mall_sku.sku_points_price > t_mall_commodity.points_price ——
--   这个库里「规格盖商品」已经是一条认识，覆盖价不该另发明一套。
-- =====================================================================================

CREATE TABLE IF NOT EXISTS `t_mall_grade_price` (
    `id`            bigint      NOT NULL AUTO_INCREMENT COMMENT 'id',
    `commodity_id`  bigint      NOT NULL COMMENT '关联 t_mall_commodity.id',
    `sku_id`        bigint      NOT NULL DEFAULT 0
        COMMENT '关联 t_mall_sku.id；0 = 整个商品（不是 NULL，理由见表头注释）',
    `grade_code`    int         NOT NULL COMMENT '对应 t_member_grade.grade_code。不允许 0 档',
    `points_price`  int         NOT NULL COMMENT '这一档就这个价（积分）。0 = 这一档免费',
    `create_by`     varchar(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`   datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     varchar(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`   datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_t_mall_gd_price` (`commodity_id`, `sku_id`, `grade_code`),
    KEY `idx_t_mall_gd_price_cmd` (`commodity_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='商城-等级覆盖价';

/*
 * 🔴 points_price 允许 0：0 = 这一档免费兑换，是合法取值。
 *   与 t_mall_sku.sku_points_price 那两列的判空同一条铁律 ——
 *   区别是那两列用 NULL 表示「没配」，这张表【整行存在与否】就是「配没配」，
 *   所以这里 NOT NULL，不需要再拿 0 去兼职。
 *
 * ⚠️ 不建外键。与 t_mall_order.coupon_id / address_id 同一个做法：
 *   商品与 SKU 都不会被物理删除（下架走 status），悬空风险本来就不存在，
 *   加外键换来的只是每次改价一次跨表锁。
 *   —— 但这意味着【数据库不会帮我们删】。所以删商品那条路上是【代码】在删：
 *      MallCommodityService.delete 里有一句 delete by commodity_id。
 *      不删的话它们是一堆 commodity_id 指向空气的孤儿，查不到也显示不出来，
 *      直到某天有人用同一个自增 id 建了新商品 —— 那件新商品会凭空带上前任的特价。
 */
