CREATE TABLE `t_commodity_category`
(
    `id`            bigint(20) NOT NULL AUTO_INCREMENT COMMENT '分类主键ID',
    `parent_id`     bigint(20) NOT NULL DEFAULT '0' COMMENT '父级ID (0表示顶级分类)',

    `category_name` varchar(50) NOT NULL COMMENT '分类名称 (如: 数码3C, 虚拟权益)',
    `category_icon` varchar(255) DEFAULT NULL COMMENT '分类图标URL (用于C端宫格导航)',
    `sort`          int(11) NOT NULL DEFAULT '0' COMMENT '排序 (从小到大)',
    `status`        tinyint(1) NOT NULL DEFAULT '1' COMMENT '状态: 0-禁用, 1-启用',

    -- SmartAdmin 框架审计字段
    `create_user`   bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time`   datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user`   bigint(20) DEFAULT NULL COMMENT '更新人',
    `update_time`   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted_flag`  tinyint(1) NOT NULL DEFAULT '0' COMMENT '删除标记: 0-未删, 1-已删',

    PRIMARY KEY (`id`),
    KEY             `idx_parent_id` (`parent_id`),
    KEY             `idx_sort` (`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城商品分类表';
CREATE TABLE `t_commodity`
(
    `id`              bigint(20) NOT NULL AUTO_INCREMENT COMMENT '商品主键ID',
    `category_id`     bigint(20) NOT NULL COMMENT '分类ID',

    -- 基础属性
    `commodity_type`  tinyint(1) NOT NULL DEFAULT '1' COMMENT '商品类型: 1-实物商品(需物流), 2-虚拟商品(发卡密/直充)',
    `commodity_name`  varchar(128)   NOT NULL COMMENT '商品名称',
    `commodity_cover` varchar(255)   NOT NULL COMMENT '商品封面主图',
    `commodity_intro` varchar(255)            DEFAULT NULL COMMENT '副标题/一句话卖点',

    -- 商业定价体系
    `pay_type`        tinyint(1) NOT NULL DEFAULT '1' COMMENT '支付方式: 1-纯积分, 2-积分+现金',
    `original_price`  decimal(10, 2) NOT NULL COMMENT '划线原价 (仅作前端展示如: 价值￥199)',
    `points_price`    int(11) NOT NULL DEFAULT '0' COMMENT '基础兑换所需积分',
    `cash_price`      decimal(10, 2) NOT NULL DEFAULT '0.00' COMMENT '基础兑换所需现金 (若pay_type=1则为0)',

    -- 门槛与控制体系 (取代笨重的活动表)
    `limit_per_user`  int(11) NOT NULL DEFAULT '0' COMMENT '单人终身限兑数量: 0表示不限制',
    `start_time`      datetime                DEFAULT NULL COMMENT '兑换开始时间 (空则代表长期在线)',
    `end_time`        datetime                DEFAULT NULL COMMENT '兑换结束时间 (空则代表长期在线)',
    `is_home`         tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否首页推荐: 0-否, 1-是',
    `status`          tinyint(1) NOT NULL DEFAULT '1' COMMENT '状态: 0-下架, 1-上架',
    `sort`            int(11) NOT NULL DEFAULT '0' COMMENT '排序权重',

    -- 动态规则
    `vip_rules`       json                    DEFAULT NULL COMMENT '等级差异化定价规则(JSON), 例: [{"level":"铂金","points":800}]',

    -- SmartAdmin 框架审计字段
    `create_user`     bigint(20) DEFAULT NULL,
    `create_time`     datetime                DEFAULT CURRENT_TIMESTAMP,
    `update_user`     bigint(20) DEFAULT NULL,
    `update_time`     datetime                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted_flag`    tinyint(1) NOT NULL DEFAULT '0',

    PRIMARY KEY (`id`),
    KEY               `idx_category` (`category_id`),
    KEY               `idx_status_sort` (`status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分商城商品主表';
CREATE TABLE `t_commodity_sku`
(
    `id`               bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'SKU主键ID',
    `commodity_id`     bigint(20) NOT NULL COMMENT '关联商品主表ID',

    -- SKU 规格定义
    `sku_attrs`        json NOT NULL COMMENT '规格属性组合(JSON), 如: {"颜色":"星空灰", "尺码":"XL"}。无规格商品填 {}',
    `sku_cover`        varchar(255)   DEFAULT NULL COMMENT '该规格专属图片 (用于C端用户切换规格时改变主图)',

    -- SKU 独立定价 (应对大码加钱、特殊颜色加价等业务)
    `sku_points_price` int(11) DEFAULT NULL COMMENT '本规格所需积分(为空则默认继承主表的 points_price)',
    `sku_cash_price`   decimal(10, 2) DEFAULT NULL COMMENT '本规格所需现金(为空则默认继承主表的 cash_price)',

    -- 高并发库存体系
    `total_stock`      int(11) NOT NULL DEFAULT '0' COMMENT '总库存',
    `locked_stock`     int(11) NOT NULL DEFAULT '0' COMMENT '锁定库存 (防超卖：下单锁定，付款/扣减成功后释放并扣总库存)',
    `available_stock`  int(11) GENERATED ALWAYS AS ((`total_stock` - `locked_stock`)) VIRTUAL COMMENT '可用库存 (MySQL虚拟列，自动计算)',
    `version`          int(11) NOT NULL DEFAULT '0' COMMENT '乐观锁版本号 (每次成功扣减库存时 +1)',

    -- SmartAdmin 框架审计字段
    `create_user`      bigint(20) DEFAULT NULL,
    `create_time`      datetime       DEFAULT CURRENT_TIMESTAMP,
    `update_user`      bigint(20) DEFAULT NULL,
    `update_time`      datetime       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted_flag`     tinyint(1) NOT NULL DEFAULT '0',

    PRIMARY KEY (`id`),
    KEY                `idx_commodity` (`commodity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品SKU与高并发库存表';
CREATE TABLE `t_commodity_image`
(
    `id`           bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `commodity_id` bigint(20) NOT NULL COMMENT '关联商品ID',
    `image_url`    varchar(255) NOT NULL COMMENT '图片URL地址',
    `image_type`   tinyint(1) NOT NULL DEFAULT '1' COMMENT '图片类型: 1-顶部轮播主图, 2-图文详情长图',
    `sort`         int(11) NOT NULL DEFAULT '0' COMMENT '排序权重 (从小到大)',

    -- SmartAdmin 框架审计字段
    `create_user`  bigint(20) DEFAULT NULL,
    `create_time`  datetime DEFAULT CURRENT_TIMESTAMP,
    `update_user`  bigint(20) DEFAULT NULL,
    `update_time`  datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted_flag` tinyint(1) NOT NULL DEFAULT '0',

    PRIMARY KEY (`id`),
    KEY            `idx_commodity_type` (`commodity_id`, `image_type`),
    KEY            `idx_sort` (`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品视觉图册表';