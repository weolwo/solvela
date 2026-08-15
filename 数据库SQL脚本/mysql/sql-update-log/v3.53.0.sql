-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- 文件模块重构 · 数据模型（2026-08-10）
--
-- 配套设计文档：docs/文件模块-架构设计文档.md §4 / §9
--
-- 本次解决的是「三个概念糊在一起」：存储层认识 HTTP 类型、业务权限编码在文件路径里、
-- 文件名有三个互相矛盾的真相源。拆开之后有 8 个既有缺陷是自动消失的，不需要单独修。
--
-- 五张表的变更：
--   ① t_file_category   新增。分类从硬编码枚举变成可配置表（可改名、可排序、可加标签）
--   ② t_file            改造。补 storage_kind / status / visibility / hash / 尺寸，修 file_size 类型
--   ③ t_file_relation   新增。文件↔业务引用，取代业务表里逗号拼接 fileKey 的做法
--   ④ t_activity_display 新增。活动 C 端展示配置（t_activity_config 的 1:1 垂直分表）
--
-- ⚠️ 时钟源沿用 v3.38.0 的口径：create_time / update_time 一律由数据库产生，Java 侧不填充。

-- ============================================================================
-- ① t_file_category  文件分类
-- ============================================================================
--
-- category_code 是本表最重要的字段：代码必须引用 code（"NOTICE"），绝不能引用自增 ID ——
-- ID 由各环境数据库各自生成，dev 上「公告」是 2、prod 上可能是 7，而代码是同一份。
-- 这是"枚举变表"最经典的翻车点。
--
-- 排序两条纪律：
--   · 查询必须 ORDER BY sort ASC, category_id ASC。第二排序键不能省 —— 只按 sort 排，
--     相同值时 MySQL 返回顺序不保证，表现是"每次刷新文件夹顺序都在变"，极难排查。
--   · sort 不加唯一索引。拖拽的中间态必然有重复值，加了就永远存不进去。
--
-- 内置分类的删除保护不在这里做，放在代码的 SYSTEM_CODES 里 —— 既然代码本来就按 code 引用，
-- 真相源留在代码侧比留在可被手工 UPDATE 掉的 DB 标记里更可靠。

DROP TABLE IF EXISTS `t_file_category`;
CREATE TABLE `t_file_category`
(
    `category_id`   bigint      NOT NULL AUTO_INCREMENT,
    `category_code` varchar(50) NOT NULL COMMENT '稳定标识，代码引用它而非ID',
    `category_name` varchar(50) NOT NULL COMMENT '显示名称，可随时改',
    `category_tag`  varchar(32)          DEFAULT NULL COMMENT '标签，选择器里分组用',
    `sort`          int         NOT NULL DEFAULT 0 COMMENT '排序，小的在前',
    `create_by`     varchar(32)          DEFAULT NULL COMMENT '创建人（用户名）',
    `create_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     varchar(32)          DEFAULT NULL COMMENT '更新人（用户名）',
    `update_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`category_id`),
    UNIQUE KEY `uk_code` (`category_code`)
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='文件分类';

-- 内置分类的 ID 直接对齐现有 folder_type 的值（FileFolderTypeEnum：1通用 2公告 3帮助中心 4意见反馈）。
-- 新旧编号是同一个数，任何漏改的地方结果都一样 —— 这让下面的平移几乎无风险。
INSERT INTO `t_file_category` (`category_id`, `category_code`, `category_name`, `category_tag`, `sort`)
VALUES (1, 'COMMON', '通用', '系统', 10),
       (2, 'NOTICE', '公告', '系统', 20),
       (3, 'HELP_DOC', '帮助中心', '系统', 30),
       (4, 'FEEDBACK', '意见反馈', '系统', 40);

-- ============================================================================
-- ② t_file  改造
-- ============================================================================

-- 2.1 重命名：file_name / file_key 这两个名字本身语义不明，是所有混乱的起点。
--     新名字把「用户给的原名」和「系统生成的存储键」分得清清楚楚。
--     original_name 顺带从 varchar(100) 放宽到 200（应用层的 FILE_NAME_MAX_LENGTH 也随之调整）。
ALTER TABLE `t_file`
    CHANGE COLUMN `file_name` `original_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户上传时的原始文件名',
    CHANGE COLUMN `file_key` `storage_key` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '存储键，系统生成，不可变';

-- 2.2 file_size 原本是有符号 int（上限 2.1GB），而实体是 Long —— 类型不匹配没有任何理由保留。
--     现在限 10MB 撞不到，一旦支持大文件就是静默溢出。
ALTER TABLE `t_file`
    MODIFY COLUMN `file_size` bigint NULL DEFAULT NULL COMMENT '字节数';

-- 2.3 新增列
--
-- storage_kind 是本次最关键的补充：不存它的话，系统从 local 切到 cloud 之后，
-- 历史文件还躺在本地磁盘、新文件在 S3，而代码只能按当前全局配置去读 ——
-- 切换那一刻所有历史文件立即失效。
--
-- tags 的存储格式是「前后各带一个逗号」：,双十一,大促,banner,
-- 查询必须用 LIKE '%,618,%' 而不是 LIKE '%618%'，否则搜「618」会命中「6180」、
-- 搜「11」会命中「双11」和「1111」。这种错不报警，只是搜出一堆不相干的东西。
ALTER TABLE `t_file`
    ADD COLUMN `category_id`   bigint       NULL COMMENT '分类，取代 folder_type' AFTER `file_id`,
    ADD COLUMN `storage_kind`  varchar(16)  NOT NULL DEFAULT 'LOCAL' COMMENT '存储介质：LOCAL / S3',
    ADD COLUMN `content_type`  varchar(100) NOT NULL DEFAULT 'application/octet-stream' COMMENT '嗅探出的真实MIME',
    ADD COLUMN `extension`     varchar(16)  NOT NULL DEFAULT '' COMMENT '扩展名，从嗅探MIME反推',
    ADD COLUMN `content_hash`  char(64)     NULL COMMENT 'SHA-256，去重预留',
    ADD COLUMN `image_width`   int          NULL COMMENT '图片宽，非图片为NULL',
    ADD COLUMN `image_height`  int          NULL COMMENT '图片高',
    ADD COLUMN `visibility`    tinyint      NOT NULL DEFAULT 1 COMMENT '1公开 2私有',
    ADD COLUMN `status`        tinyint      NOT NULL DEFAULT 1 COMMENT '1临时 2已确认',
    ADD COLUMN `tags`          varchar(500) NULL COMMENT '标签，前后各带逗号：,双十一,banner,',
    ADD COLUMN `deleted_flag`  tinyint      NOT NULL DEFAULT 0 COMMENT '删除标记',
    ADD COLUMN `create_by`     varchar(32)  NULL COMMENT '创建人（用户名）',
    ADD COLUMN `update_by`     varchar(32)  NULL COMMENT '更新人（用户名）';

-- 2.4 数据平移
UPDATE `t_file` SET `category_id` = `folder_type` WHERE `category_id` IS NULL;

-- 存量一律置为「已确认」。⚠️ 漏了这一步，TEMP 清理任务会把所有历史文件删光。
UPDATE `t_file` SET `status` = 2;

-- 权限从路径前缀里解放出来。历史 key 形如 private/common/xxx 或 public/common/xxx
UPDATE `t_file` SET `visibility` = 2 WHERE `storage_key` LIKE 'private/%';

-- 扩展名优先取旧的 file_type 列（它存的就是扩展名），为空时再从 key 的最后一段兜底
UPDATE `t_file` SET `extension` = LEFT(`file_type`, 16) WHERE `file_type` IS NOT NULL AND `file_type` <> '';
UPDATE `t_file`
SET `extension` = LEFT(SUBSTRING_INDEX(`storage_key`, '.', -1), 16)
WHERE `extension` = ''
  AND SUBSTRING_INDEX(`storage_key`, '/', -1) LIKE '%.%';

-- create_by 存用户名，而旧的 creator_name 存的正是 RequestUser#getUserName()（即 t_employee.actual_name），
-- 两者语义完全一致，直接平移零信息损失。
-- creator_id / creator_name / creator_user_type 保留一个版本双写观察，档⑤ 清退旧实现时一并删除。
UPDATE `t_file` SET `create_by` = `creator_name` WHERE `creator_name` IS NOT NULL;

-- ⚠️⚠️ 下面这条必须按各环境的实际存储填，不能一刀切执行 ⚠️⚠️
-- 当前四套环境的 file.storage.mode 全是 local，所以默认值 'LOCAL' 已经正确；
-- 若某环境已经在用云存储，先执行：
--     UPDATE `t_file` SET `storage_kind` = 'S3';

-- 2.5 索引
-- 原 module_id_module_type(folder_type) 是单列低基数索引（只有 4 个值），选择性极差，
-- 优化器基本不会选中。换成真正会被用到的组合索引。
ALTER TABLE `t_file`
    DROP INDEX `module_id_module_type`,
    ADD KEY `idx_category_time` (`category_id`, `create_time` DESC),
    ADD KEY `idx_status_time` (`status`, `create_time`),
    ADD KEY `idx_hash` (`content_hash`);

-- ============================================================================
-- ③ t_file_relation  文件业务关联
-- ============================================================================
--
-- 取代业务表里「逗号分隔的 fileKey 字符串」（如 t_notice.attachment）。那个做法在单个上传时
-- 看不出问题，批量并发一来立刻暴露：每个附件都是「读 attachment → 拼上自己 → 写回」，
-- 典型的读改写竞态，必丢且丢得很安静 —— 用户传了 5 个只看到 3 个。
--
-- idx_file 支持反查「这个文件被谁引用着」，是安全删除的前提。
-- 逗号字符串永远做不到这件事，所以现在这套系统实际上不敢删任何文件。

DROP TABLE IF EXISTS `t_file_relation`;
CREATE TABLE `t_file_relation`
(
    `relation_id` bigint      NOT NULL AUTO_INCREMENT,
    `file_id`     bigint      NOT NULL,
    `biz_type`    varchar(50) NOT NULL COMMENT '业务类型：NOTICE / HELP_DOC / ACTIVITY_DISPLAY / ...',
    `biz_id`      bigint      NOT NULL,
    `sort`        int         NOT NULL DEFAULT 0 COMMENT '附件顺序，轮播图必需',
    `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`relation_id`),
    UNIQUE KEY `uk_biz_file` (`biz_type`, `biz_id`, `file_id`),
    KEY `idx_file` (`file_id`)
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='文件业务关联';

-- ============================================================================
-- ④ t_activity_display  活动C端展示配置
-- ============================================================================
--
-- t_activity_config 的 1:1 垂直分表。为什么不往主表加列：
-- 主表是热表（C 端每次进活动都要查状态和起止时间），而 rule_content 是富文本、几 KB 到几十 KB。
-- InnoDB 一个数据页 16KB，行越宽单页装的行越少；而 MyBatis-Plus 默认 SELECT * 会把
-- 每次状态查询都拖上几十 KB 的规则文本。
--
-- 列 vs JSON 的划分标准只有一条：
--   程序逻辑要读的、要检索的 → 独立列；纯透传给前端渲染的 → JSON。
--
-- extra_config 的纪律：后端只存不解析。一旦开始 JSON_EXTRACT 取值做业务判断，
-- 它就退化成一堆没有约束的隐式契约，将来谁都不敢动。真需要后端读的，升格成独立列。
--
-- 图片一律存 file_id 而不是 URL：存 URL 意味着换 CDN 域名要洗全表、local 切 cloud 要洗全表、
-- 想要缩略图拼不出来。将来推 CDN 的 JSON 里需要绝对 URL 时，走「编辑态存引用、发布态存快照」。
--
-- 刻意不加 version / publish_status：将来的发布快照与本表内容形态不同（本表存 fileId，
-- 快照存渲染后的绝对 URL），版本化应该是独立表存渲染后 JSON，不是本表的历史副本。
-- 现在加那两个字段的结果是它们永远等于默认值，将来做版本时还要洗一遍。留白比留错字段好。

DROP TABLE IF EXISTS `t_activity_display`;
CREATE TABLE `t_activity_display`
(
    `id`             bigint      NOT NULL AUTO_INCREMENT,
    `tenant_id`      varchar(16) NOT NULL DEFAULT '0' COMMENT '冗余租户，让数据权限拦截统一',
    `activity_id`    bigint      NOT NULL COMMENT '关联 t_activity_config.id',

    `main_image_id`  bigint               DEFAULT NULL COMMENT '主视觉 file_id',
    `bg_image_id`    bigint               DEFAULT NULL COMMENT '背景图 file_id',
    `share_image_id` bigint               DEFAULT NULL COMMENT '分享图 file_id',
    `share_title`    varchar(64)          DEFAULT NULL COMMENT '分享标题',
    `share_desc`     varchar(128)         DEFAULT NULL COMMENT '分享描述',
    `sub_title`      varchar(128)         DEFAULT NULL COMMENT '副标题',
    `theme_color`    varchar(16)          DEFAULT NULL COMMENT '主题色 #RRGGBB',
    `rule_content`   mediumtext           COMMENT '活动规则，富文本HTML。禁止 base64 内联图片',

    `extra_config`   json                 DEFAULT NULL COMMENT '按 activity_type 各自定义，后端只存不解析',

    `create_by`      varchar(32)          DEFAULT NULL COMMENT '创建人（用户名）',
    `create_time`    datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      varchar(32)          DEFAULT NULL COMMENT '更新人（用户名）',
    `update_time`    datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_activity` (`activity_id`)
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='活动C端展示配置';

-- ============================================================================
-- 自查：以下每条都应返回 0 行
-- ============================================================================

-- ① 没有对上分类的文件
-- SELECT COUNT(*) FROM t_file WHERE category_id IS NULL
--    OR category_id NOT IN (SELECT category_id FROM t_file_category);

-- ② 存量文件被误判为临时（会被清理任务删掉）
-- SELECT COUNT(*) FROM t_file WHERE status <> 2;

-- ③ 扩展名没提取出来的（正常应只剩本来就没扩展名的文件）
-- SELECT COUNT(*) FROM t_file WHERE extension = '' AND SUBSTRING_INDEX(storage_key,'/',-1) LIKE '%.%';

-- ④ 存储介质没填对：确认与本环境 file.storage.mode 一致
-- SELECT storage_kind, COUNT(*) FROM t_file GROUP BY storage_kind;

-- ⑤ 创建人没平移过来的
-- SELECT COUNT(*) FROM t_file WHERE create_by IS NULL AND creator_name IS NOT NULL;
