-- ⚠️⚠️ 本文件<b>不是权威定义</b>。权威是 mysql/schema-baseline.sql（从真库导出、空库验证过）。
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
-- 会员域 DDL
-- ============================================================================
--
-- 这是<b>全域基础设施</b>，不属于商城：钱包(t_member_wallet)、券(t_member_coupon)、
-- 流水(t_member_asset_transaction)、任务(t_task_record)、提案(t_proposal_record)、
-- 履约(t_physical_delivery)、商城(t_mall_*) 全部依赖它。所以单独成文件，
-- 与 activity.sql / lottery.sql / mall.sql 平级。
--
-- ----------------------------------------------------------------------------
-- 关于 tenant_id：从「惰性占位」转正为「真实字段」
-- ----------------------------------------------------------------------------
-- 现状盘点（实测）：库中 25 张表带 tenant_id，共 9317 行，<b>tenant_id <> '0' 的行数 = 0</b>；
-- MybatisPlusConfig 里只有分页拦截器、没有租户拦截器；Mapper 里那句
-- `AND tenant_id = #{queryForm.tenantId}` 在 <if> 中，前端从不传，永不生效。
-- 也就是说它一直是个占位符 —— 既没进索引，也没进任何过滤条件。
--
-- 现在把它转正，做两件事：
--   ① 默认租户从无意义的 '0' 改成 <b>'taozi'</b>，让它看起来就是个真实租户而不是"未启用"
--   ② 唯一索引带上 tenant_id —— 这是<b>语义正确性</b>问题，不是性能优化，见下
--
-- 【索引加不加 tenant_id，分三种情况，不要一刀切】
--
--   ✅ <b>唯一索引：必须加</b>。这是语义问题。
--      uk_mbr_phone_hash(phone_hash) 的含义是「全平台一个手机号只能一个账号」；
--      改成 (tenant_id, phone_hash) 才是「每个租户内一个手机号一个账号」。
--      ⚠️ 这是<b>行为变更</b>：加了之后，同一个人可以在不同租户各注册一个账号。
--         多租户系统通常就该这样（租户之间是不同的品牌/客户），但如果业务上要求
--         「一个手机号全平台唯一」，那就<b>不能加</b>。这条要按业务拍板，不是技术决定。
--
--   ✅ <b>按租户扫描的列表索引：加</b>，放最左。
--      后台会员列表是 `WHERE tenant_id=? AND status=? ORDER BY create_time`，
--      tenant_id 不在最左的话，多租户后这个索引就等于没有。
--
--   ❌ <b>全局唯一键的点查索引：不加</b>。
--      member_id 由全局发号器产生、跨租户唯一，`WHERE member_id=?` 已经唯一定位，
--      再加 tenant_id 只是给每个索引项白搭 6 字节、零收益。
--      风控类索引（同 IP 撞库）更是<b>刻意跨租户</b>查才有意义。
--
-- 【切换默认租户的执行顺序，别搞反】
--   1. ALTER 所有表的 DEFAULT '0' → 'taozi'（INSTANT，毫秒级）
--   2. UPDATE 全部存量：`UPDATE t_xxx SET tenant_id='taozi' WHERE tenant_id='0'`
--   3. 改代码里硬编码的 "0" 与 DEFAULT_TENANT_ID 常量（实测 ~18 处 setTenantId）
--   🔴 1、2 必须在<b>同一个维护窗口</b>内做完。'0' 和 'taozi' 两种值共存的中间态
--      是最危险的 —— 一旦此时启用了租户拦截器，老数据会集体"消失"。
--   💡 大小写：项目里 varchar 字典值惯例是全大写（PHYSICAL/NORMAL/LIFETIME）。
--      想对齐就用 'TAOZI'，想保留原样就 'taozi' —— 只要<b>全库一致</b>，选哪个都行。
--
-- ----------------------------------------------------------------------------
-- 标识体系：三个字段，各司其职
-- ----------------------------------------------------------------------------
--   member_id    bigint       10位数字。<b>全链路关联键 + 迁库/合并的锚点</b>。永不可变，用户基本不用管
--   member_name  varchar(32)  微信号风格的账号：字母开头、可读、可分享、全局唯一。用户可改（限频）
--   nickname     varchar(64)  中文昵称，随便改。<b>任何地方都不许拿它做键</b>
--
-- 三者的分工可以这样记：
--   member_id   给<b>机器</b>用 —— join、外键、对账、迁库合并
--   member_name 给<b>人</b>用   —— 加好友、客服报账号、分享、找回
--   nickname    给<b>眼睛</b>用 —— 界面上显示的那串字
--
-- ----------------------------------------------------------------------------
-- 🔴 关联键必须是 member_id，不能是 member_name。这正是 member_id 存在的意义
-- ----------------------------------------------------------------------------
-- 现状：系统此前没有会员主表，九张已部署的表用 `member_name varchar(64)` 当关联键
--   （t_member_wallet / t_member_asset_transaction / t_member_coupon /
--     t_physical_delivery / t_proposal_record / t_task_record /
--     t_prize_log / t_draw_prize_log / t_lottery_record）。
--
-- 一旦 member_name 变成「微信号那种可读、可改的账号」，拿它当关联键就有两个致命问题：
--
--   ① <b>改名即断链</b>。微信号一年能改一次，改完九张表的历史数据全部指向一个
--      不存在的账号。不报错，只是查不到了 —— 钱包余额还在，但这个人查不出自己的流水。
--
--   ② <b>合并库时必然撞车</b>，而这恰恰是你说的「某天要迁库或者合并」那个场景。
--      A 库有 zhangsan、B 库也有 zhangsan，两个人不是同一个人。
--      · 关联键是 member_name → 冲突无法自动解决，必须让某一方用户改微信号。
--        这是产品灾难：用户凭什么因为你合并数据库就得改自己的号。
--      · 关联键是 member_id  → 给冲突方重新发号 + 留一张映射表，用户<b>完全无感</b>，
--        名字还是原来那个名字。
--
--   所以：member_id 才是那个稳定锚点，member_name 是给人看的门牌号。
--   门牌号可以改，房子的产权编号不能改。
--
-- 【迁移成本：现在做是最便宜的时刻，而且只会越来越贵】
--   · 会员表本来就还没建，member_id 现在才第一次出现，不存在「回填历史 ID」的问题
--   · 存量 member_name 全是开发期自造的测试值，不是真实用户数据
--   · 商城那四张表（t_mall_order / t_mall_exchange_limit / t_mall_address /
--     t_mall_favorite）<b>还没上线，改是零成本</b>
--   具体迁移脚本见文件末尾「附二」。
-- ============================================================================


-- ============================================================================
-- 1. t_member  会员主表
-- ============================================================================
DROP TABLE IF EXISTS `t_member`;
CREATE TABLE `t_member`
(
    -- 🔴 刻意<b>不是</b> AUTO_INCREMENT，由应用层发号。理由见「附一：会员号怎么发」
    `member_id`        bigint      NOT NULL COMMENT '会员号：10位数字(1000000000~9999999999)。全链路关联键+迁移锚点，永不可变',
    `tenant_id`        varchar(16) NOT NULL DEFAULT 'taozi' COMMENT '租户id：默认租户 taozi',

    -- ---------- 账号：微信号风格，给人用 ----------
    -- 规则见「附三：member_name 规则」。注册时系统自动生成一个，用户后续可改（限频）。
    -- 🔴 显式指定 ci 排序规则：微信号 ZhangSan 与 zhangsan 必须视为同一个账号。
    --    不写死的话，将来有人把库改成 _bin/_cs 排序规则，唯一约束会静默放过大小写变体，
    --    于是出现两个肉眼看着一模一样的账号 —— 客服根本没法处理。
    -- 🔴 用 utf8mb4_0900_ai_ci 而<b>不是</b> utf8mb4_general_ci：
    --    本库默认就是 0900_ai_ci（十张存量表的 member_name 全是它），它本身已经是
    --    大小写不敏感（ci），完全满足上面的诉求。而写成 general_ci 会与存量表<b>不一致</b>，
    --    迁移时的 `JOIN ... ON m.member_name = x.member_name` 会直接抛
    --    「Illegal mix of collations」—— 排序规则不一致的两列<b>不能直接比较</b>。
    --    这类错误只在跨表 JOIN 时才暴露，建表当下完全看不出来。
    `member_name`      varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL
                                   COMMENT '账号：微信号风格，字母开头6~20位[A-Za-z][A-Za-z0-9_-]。全局唯一(大小写不敏感)，用户可改',
    `name_update_time` datetime             DEFAULT NULL COMMENT '上次修改账号的时间：改名限频判据(建议一年一次)。为空表示从未改过',

    -- ---------- 展示信息（随便改，不许做键）----------
    `nickname`         varchar(64) NOT NULL COMMENT '昵称：中文随意，用户可改。🔴任何地方都不许拿它做关联键',
    `avatar_file_id`   bigint               DEFAULT NULL COMMENT '头像 file_id（走文件模块，同商城图片）',
    `gender`           tinyint     NOT NULL DEFAULT 0 COMMENT '性别：0-未知, 1-男, 2-女',
    `birthday`         date                 DEFAULT NULL COMMENT '生日：生日营销用，可空',

    -- ---------- 联系方式：密文 + hash 双写 ----------
    -- 明文存手机号是最典型的合规问题；但加密后无法做唯一索引、也无法按手机号登录。
    -- 所以密文给「读出来展示/发短信」，hash 给「唯一约束 + 登录查询」，两者必须并存。
    --
    -- 🔴 类型是 binary(32)，不是 char(64)。理由与实测见「附六：hash 列怎么选」——
    --    一句话：hex 编码白白把索引撑大 8 倍，而 binary 存的是原始摘要字节。
    -- 🔴 算法是 HMAC-SHA256(密钥, 手机号)，不是 SHA-256(手机号+盐)。密钥与数据库
    --    分开保管（配置中心/KMS，别和库备份放一起）。原因同样见「附六」。
    --    国密合规场景换 HMAC-SM3，输出同为 32 字节，本列定义不用改。
    `phone`            varchar(255)         DEFAULT NULL COMMENT '手机号密文（AES/SM4，密钥走配置）',
    `phone_hash`       binary(32)           DEFAULT NULL COMMENT '手机号HMAC-SHA256原始字节(32B)：唯一约束与登录查询走它。注销时置NULL以释放号码。查看用HEX()',
    `email`            varchar(255)         DEFAULT NULL COMMENT '邮箱密文，可空',
    `email_hash`       binary(32)           DEFAULT NULL COMMENT '邮箱HMAC-SHA256原始字节(32B)，可空',

    -- ---------- 登录凭证 ----------
    -- 积分商城普遍是「手机号+验证码」登录，未必设密码，故可空。
    -- 有密码时算法对齐 t_employee 的 Argon2id（见 SecurityPasswordService），不要另起一套。
    --
    -- 🔴 <b>没有 salt 列，也不要加</b> —— Argon2 的 PHC 串已经把每次随机生成的盐带在里面了。
    --    实测（Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8，同一密码连编两次）：
    --      $argon2id$v=19$m=16384,t=2,p=1$y2vswzZcQvEfyZLd6d8O2w$4tOQSPWMJN9q/GPiM6tJxID...
    --      $argon2id$v=19$m=16384,t=2,p=1$dQfJ/QrAMzRGg7P60d0uEg$ob1hGesxesII5373uP02nRr...
    --                                     └── 第4段就是盐(16字节base64)，每次都不同 ──┘
    --    两串都能 matches() 通过。再开一列 salt 是重复，而且是<b>有害</b>的重复：
    --    两个真相源，将来没人说得清校验时用的是哪一个。t_employee 也只有 login_pwd 一列。
    --    （password+salt 两列是 MD5/SHA1 时代的遗产 —— 那时哈希没有标准封装格式，
    --      只能自己存盐。bcrypt/scrypt/Argon2 都用 PHC 串把盐带在自己身上。）
    --
    -- 🔴 长度给 255 而不是抄 t_employee 的 100：上面那串实测 <b>97 字符</b>，
    --    只剩 3 个字符余量。哪天调高 Argon2 参数（m/t/p 位数变多）就会溢出，
    --    而 MySQL 非严格模式下是<b>静默截断</b> —— 之后所有人登录都失败，且完全查不出原因。
    --    varchar 按实际长度存，留到 255 不占任何空间。
    `password`         varchar(255)         DEFAULT NULL COMMENT '登录密码：Argon2id PHC串(盐已内嵌，不要再开salt列)。验证码登录可为空',

    -- ---------- 状态 ----------
    -- 🔴 没有 deleted_flag：会员不物理删也不软删，注销是 status=3。理由见「附四：注销」
    `status`           tinyint     NOT NULL DEFAULT 1 COMMENT '状态：1-正常, 2-冻结(风控/违规), 3-已注销',

    -- ---------- 来源与风控 ----------
    -- 渠道归因：拉新活动的效果全靠它算，事后加没法回填
    `register_source`  varchar(32) NOT NULL DEFAULT 'UNKNOWN' COMMENT '注册来源渠道：H5/APP/WECHAT/INVITE/IMPORT...',
    `register_ip`      varchar(64)          DEFAULT NULL COMMENT '注册IP：批量注册的识别依据',
    `invite_id`        bigint               DEFAULT NULL COMMENT '邀请人member_id：没有邀请体系时恒为空，留着比事后加表便宜',
    -- 🔴 last_login_time / last_login_ip 已移出本表，见 t_member_login_log。
    --    主表是<b>热读表</b>（每次鉴权都读），不能挂高频写字段。

    `remark`           varchar(255)         DEFAULT NULL COMMENT '运营备注',

    `create_by`        varchar(64)          DEFAULT NULL COMMENT '创建人：后台导入时有值，自主注册为空',
    `create_time`      datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    `update_by`        varchar(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`      datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (`member_id`),
    -- 账号全局唯一。排序规则是 ci，所以天然大小写不敏感
    -- 🔴 注销后<b>不释放</b>账号（与手机号相反），理由见「附四」
    UNIQUE KEY `uk_mbr_name` (`tenant_id`, `member_name`),
    -- 一个手机号一个账号。注销时 phone_hash 置 NULL 释放号码
    -- ——MySQL 的 UNIQUE 允许多个 NULL，这正是我们要的行为
    UNIQUE KEY `uk_mbr_phone_hash` (`tenant_id`, `phone_hash`),
    UNIQUE KEY `uk_mbr_email_hash` (`tenant_id`, `email_hash`),
    -- 后台会员列表默认按注册时间倒序 + 按状态筛选
    KEY `idx_mbr_status_time` (`tenant_id`, `status`, `create_time`),
    -- 渠道效果报表
    KEY `idx_mbr_source_time` (`tenant_id`, `register_source`, `create_time`),
    KEY `idx_mbr_invite` (`invite_id`)
) COMMENT ='会员主表';

-- ⚠️ 刻意<b>没有</b>的字段，别加：
--   · points / balance —— 资产在 t_member_wallet（一行一种资产）。在主表冗余余额，
--     等于凭空造出第二个账实，两边对不上时你不知道该信谁。这是最容易犯也最难查的错。
--   · member_level / growth_value —— 系统没有等级体系（全库 grep 无）。真要做单独建表。
--   · real_name / id_card —— 见 t_member_verify，敏感信息不进热表。
--   · deleted_flag —— 见「附四：注销」。


-- ============================================================================
-- 2. t_member_verify  会员实名信息
-- ============================================================================
--
-- 【为什么单独一张表，而不是主表加两列】
--   ① 敏感级别不同。主表每次查会员都会被读，实名信息一年用不上几次 ——
--      混在一起等于把身份证号铺在所有查询路径上，是白送的暴露面。
--   ② 大部分会员根本没实名。塞主表就是一大片 NULL。
--   ③ 权限边界不同。运营能看会员列表，但不该能看身份证 ——
--      分表之后「谁能查实名」是一个独立的接口和独立的权限点，天然可控。
--
-- 【什么时候真的需要它】
--   积分商城发实物、发现金红包到一定额度，国内是要求实名的。
--   没这个需求前，本表可以先建不填 —— 建表成本为零，事后补建则要改发放链路。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_member_verify`;
CREATE TABLE `t_member_verify`
(
    `id`            bigint      NOT NULL AUTO_INCREMENT COMMENT 'id',
    `tenant_id`     varchar(16) NOT NULL DEFAULT 'taozi' COMMENT '租户id：默认租户 taozi',
    `member_id`     bigint      NOT NULL COMMENT '会员号',

    -- 同样是密文 + hash 双写：hash 用于「同一身份证不许注册多个账号」的唯一约束。
    -- 类型与算法口径同 t_member.phone_hash，见「附六」
    `real_name`     varchar(255)         DEFAULT NULL COMMENT '真实姓名密文',
    `id_card`       varchar(255)         DEFAULT NULL COMMENT '身份证号密文',
    `id_card_hash`  binary(32)           DEFAULT NULL COMMENT '身份证HMAC-SHA256原始字节(32B)：查重与唯一约束走它',

    `verify_status` tinyint     NOT NULL DEFAULT 0 COMMENT '认证状态：0-未认证, 1-认证中, 2-已认证, 3-认证失败',
    `verify_time`   datetime             DEFAULT NULL COMMENT '认证通过时间',
    `fail_reason`   varchar(255)         DEFAULT NULL COMMENT '认证失败原因',

    `create_time`   datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mbr_vrf_member` (`member_id`),
    -- 一张身份证只能实名一个账号（羊毛党最常见的手法就是一人多号）
    UNIQUE KEY `uk_mbr_vrf_idcard` (`tenant_id`, `id_card_hash`)
) COMMENT ='会员实名信息（敏感，与主表分离）';


-- ============================================================================
-- 3. t_member_login_log  会员登录日志
-- ============================================================================
--
-- 【为什么把 last_login_* 从主表挪出来】
--   「行锁」这个说法要稍微修一下：每次登录锁的是<b>会员自己那一行</b>，
--   用户之间不争锁，所以不是经典的锁竞争。但挪出来仍然是对的，理由更硬：
--
--   ① 🔴 <b>update_time 的语义会被彻底污染</b>（最实际的一条）。
--      主表的 update_time 挂着 ON UPDATE CURRENT_TIMESTAMP，
--      每登录一次就被刷新一次 —— 于是「这个会员资料最后修改于何时」这个信息
--      <b>永久丢失</b>，运营看到「刚更新过」其实只是这人登录了一下。
--      按登录量算，这列几乎立刻退化成 last_login_time 的副本。
--   ② <b>热读表被高频写弄脏</b>。主表每次鉴权都要读，是常驻 Buffer Pool 的热页；
--      高频 UPDATE 让这些页反复变脏，推高 checkpoint 与刷盘压力。
--   ③ <b>改一个 datetime 要重写整行</b>。InnoDB 是行级存储，主表 22 列
--      还带着几个 varchar(255) 密文，undo log 和 row 格式 binlog 都按整行走 ——
--      写放大很可观，主从延迟也跟着涨。
--   ④ 真正会争锁的场景确实存在：用户登录的<b>同时</b>运营在后台冻结/改备注这个会员，
--      那就是实打实的行锁冲突。频率不高，但一旦发生就是登录接口卡住。
--
-- 【为什么不复用已有的 t_login_log】
--   🔴 <b>装不下</b>：那张表的 user_id 是 `int`，上限 2,147,483,647，
--      而会员号范围是 1,000,000,000 ~ 9,999,999,999 ——
--      只有约 1/9 的会员号能塞进去，其余全部溢出。这是硬阻塞。
--   即使 ALTER 成 bigint，也还有两条不该混：
--     · 量级差几个数量级。员工登录一天几十条，会员一天可能几万到几十万条，
--       混一张表后员工的登录审计会被会员数据彻底淹没。
--     · 那张表的列宽是 SmartAdmin 原始设计：user_name/login_ip/login_ip_region
--       都是 varchar(1000)，remark varchar(2000)，user_agent 是 text。
--       员工量级无所谓，会员量级下这些宽列会让表膨胀得非常快。
--   所以另起一张，列宽按会员量级重新收紧。
--
-- 【只 INSERT，不 UPDATE】
--   这张表天生 append-only：没有 UPDATE 就没有行锁、没有 MVCC 版本链、
--   没有页分裂。顺带白捡安全审计能力（异地登录、设备变更、失败追踪）。
--
-- 【那「最后登录时间」和「沉默用户召回」怎么办】
--   · 详情页看最后登录：`WHERE member_id=? ORDER BY id DESC LIMIT 1`，
--     走 idx_mbr_log_member 前缀 + 主键倒序，单条查询毫秒级。
--   · 沉默用户召回（30天没登录的人）：这是<b>离线分析</b>，不是在线查询。
--     用定时任务按月聚合成结果表，或直接在报表库跑。
--     🔴 不要为了一个离线需求，在在线登录链路上加一次写 —— 那正是刚被挪走的东西。
--
-- 【必须配清理，否则它会无限涨】
--   会员登录日志是全库增长最快的表。挂 t_smart_job 定期删（如保留 6 个月），
--   或按月建分区直接 DROP PARTITION（分区删远快于 DELETE，且不产生大事务）。
--   ⚠️ 会员注销时按个保法应清除其日志；若因审计需要保留，至少把 login_ip 抹掉。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_member_login_log`;
CREATE TABLE `t_member_login_log`
(
    `id`           bigint      NOT NULL AUTO_INCREMENT COMMENT 'id',
    `tenant_id`    varchar(16) NOT NULL DEFAULT 'taozi' COMMENT '租户id：默认租户 taozi',
    -- 🔴 bigint，不是 int。会员号是 10 位数字，t_login_log 的 int 装不下
    `member_id`    bigint      NOT NULL COMMENT '会员号',

    -- ---------- 客户端环境：拆解存储，不存原始 UA ----------
    -- 39 = IPv6 标准文本最长（8组4位hex + 7个冒号）。IPv4-mapped 形式更短，够用。
    -- ⚠️ X-Forwarded-For 可能是一条 IP 链（"1.2.3.4, 5.6.7.8, ..."），
    --    取值时必须只取真实客户端那一个，别把整条链塞进来 —— 否则超长静默截断
    `client_ip`    varchar(39)          DEFAULT NULL COMMENT '客户端IP（兼容IPv6，39位足够）',
    `ip_region`    varchar(64)          DEFAULT NULL COMMENT 'IP归属地（ip2region 解析，SmartIpUtil 已有）',
    -- 拆成三列而不是存 varchar(512) 原始 UA：原始 UA 无法聚合统计（"iOS占比多少"答不了），
    -- 而且千万行量级下那一列就是纯粹的存储负担
    `device_type`  varchar(16)          DEFAULT NULL COMMENT '设备端：APP/H5/WECHAT/PC',
    `os_name`      varchar(32)          DEFAULT NULL COMMENT '操作系统：iOS/Android/Windows',
    `browser_name` varchar(32)          DEFAULT NULL COMMENT '浏览器：Chrome/Safari',

    -- ---------- 行为结果 ----------
    -- 🔴 取值与既有 t_login_log.login_result <b>正好相反</b>（那边 0-成功 1-失败）。
    --    这里跟随本项目 status 列的通行口径（1 = 正常/成功，见 t_member.status、
    --    t_mall_commodity.status），因为<b>列名不同</b>，不会被误当成同一个字典。
    --    但写代码时别照抄 LoginService 里那套 login_result 的判断，会正好搞反。
    `status`       tinyint     NOT NULL DEFAULT 1 COMMENT '状态：0-失败, 1-成功, 2-登出。⚠️与t_login_log.login_result取值相反',
    `remark`       varchar(128)         DEFAULT NULL COMMENT '提示信息：成功可为空，失败写具体原因',

    -- ---------- 链路追踪 ----------
    -- 项目已有 LogTraceFilter（MDC key "traceId"，组件扫描 "sa" 覆盖得到，确认生效）。
    -- 自生成的是 Long.toHexString(...)，最长 16 字符；但它<b>也接受请求头传入</b>，
    -- ⚠️ 那就是用户可控输入 —— 入库前必须截断到 64，否则超长时非严格模式静默截断
    `trace_id`     varchar(64)          DEFAULT NULL COMMENT '全链路追踪ID，对应 LogTraceFilter 的 MDC traceId',

    -- 🔴 没有单独的 login_time：create_time 就是登录时间（铁律 9，多一列就是多一个时钟源）。
    --    NOT NULL 是刻意的防线：铁律 9 记着「@TableField(fill) 把 null 显式写进去，
    --    实测让整列 create_time 变 NULL」那次事故 —— 日志没有时间就等于没有日志
    `create_time`  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间（即登录时间）',

    -- 🔴 主键是 (id, create_time) 而不是 (id)：
    --    <b>分区表的每一个唯一索引（主键也算）都必须包含分区键</b>，这是 MySQL 的硬约束，
    --    不满足会直接建表失败：「A PRIMARY KEY must include all columns in the table's
    --    partitioning function」。所以按 create_time 分区就必须把它并进主键。
    --    AUTO_INCREMENT 仍然合法 —— 它只要求自增列是某个索引的<b>第一列</b>，id 在最左，满足。
    PRIMARY KEY (`id`, `create_time`),
    -- 查某人最后一次登录：WHERE member_id=? ORDER BY id DESC LIMIT 1
    -- ✅ 单列就够，<b>不要写成 (member_id, id)</b> —— InnoDB 二级索引隐式带主键，
    --    同一 member_id 内天然按 id 有序。20万行实测两者 EXPLAIN 完全一致
    --    （type=ref, key_len=8, Backward index scan, 无 filesort），索引体积一字节不差
    KEY `idx_mbr_log_member` (`member_id`),
    -- 按租户+时间统计/清理。tenant_id 放最左：这是典型的"按租户扫一段时间"查询
    KEY `idx_mbr_log_time` (`tenant_id`, `create_time`),
    -- 风控：同一 IP 短时间大量登录 = 撞库或批量注册。
    -- 刻意<b>不带 tenant_id</b> —— 黑产会跨租户打，跨租户查才看得见
    KEY `idx_mbr_log_ip` (`client_ip`, `create_time`)
) COMMENT ='会员登录日志（append-only，按月分区）'
-- ----------------------------------------------------------------------------
-- 按月 RANGE 分区：清理靠 DROP PARTITION，不靠 DELETE
-- ----------------------------------------------------------------------------
-- 为什么建表时就要做：分区<b>不能</b>用 ALTER 给已有大表"补上"而不重建 ——
-- 那是一次全表 COPY，几千万行时锁表几十分钟。建表时定好，成本为零。
--
-- 用 RANGE COLUMNS(create_time) 而不是 RANGE(TO_DAYS(create_time))：
--   COLUMNS 形式直接拿 datetime 比较，边界值一眼能读懂，同样支持分区裁剪；
--   TO_DAYS() 那种写法边界是一串看不懂的整数，运维加分区时极易写错。
--
-- 🔴 pmax 是兜底，<b>不是</b>让你不用维护分区：
--   没有 pmax → 超出范围的 INSERT 直接报错，登录日志写不进去；
--   有了 pmax → 不报错，但数据全堆进这一个分区，DROP 不掉，<b>静默劣化</b>。
--   所以两件事都要做：① 定时任务提前 3 个月 REORGANIZE 出新分区；
--   ② <b>监控 pmax 的行数，> 0 就告警</b> —— 那说明分区维护已经掉队了。
--
-- 加新分区（注意是 REORGANIZE 而非 ADD，因为 pmax 占着 MAXVALUE）：
--   ALTER TABLE t_member_login_log REORGANIZE PARTITION pmax INTO (
--     PARTITION p202703 VALUES LESS THAN ('2027-04-01'),
--     PARTITION pmax    VALUES LESS THAN (MAXVALUE));
--
-- 删旧分区（保留 6 个月，秒级，不产生大事务、不写 undo、binlog 只有一条 DDL）：
--   ALTER TABLE t_member_login_log DROP PARTITION p202601;
--   —— 对比 DELETE：删几百万行会撑爆 undo log、主从延迟飙升、还留一堆碎片空洞。
--
-- ⚠️ 代价要知道：分区表的索引是<b>本地索引</b>（每个分区各有一份）。
--    `WHERE member_id=?` 不带 create_time，无法分区裁剪，要扫<b>所有分区</b>的索引。
--    分区越多这个放大越明显 —— 这正是「按月 + 只留 6 个月」的原因：
--    分区数控制在个位数，放大可接受；要是按天分区留两年，那就是 730 次索引查找。
-- ----------------------------------------------------------------------------
PARTITION BY RANGE COLUMNS (`create_time`) (
    PARTITION p202608 VALUES LESS THAN ('2026-09-01'),
    PARTITION p202609 VALUES LESS THAN ('2026-10-01'),
    PARTITION p202610 VALUES LESS THAN ('2026-11-01'),
    PARTITION p202611 VALUES LESS THAN ('2026-12-01'),
    PARTITION p202612 VALUES LESS THAN ('2027-01-01'),
    PARTITION p202701 VALUES LESS THAN ('2027-02-01'),
    PARTITION pmax    VALUES LESS THAN (MAXVALUE)
);

-- ⚠️ 本表三条索引里，只有 idx_mbr_log_time 带了 tenant_id，另两条<b>刻意不带</b>：
--    · idx_mbr_log_time(tenant_id, create_time) —— 「按租户扫一段时间」是典型的
--      统计/清理查询，tenant_id 必须在最左，否则多租户后这索引等于没有。
--    · idx_mbr_log_member(member_id) —— member_id 由全局发号器产生、<b>跨租户唯一</b>，
--      已经能唯一定位，再加 tenant_id 只是每个索引项白搭 6 字节。
--    · idx_mbr_log_ip(client_ip, create_time) —— 撞库检测<b>刻意要跨租户查</b>：
--      黑产会同时打多个租户，加了 tenant_id 反而把这条线索切断了。
--    完整取舍规则见文件顶部「关于 tenant_id」一节。
--
-- ⚠️ device_type / os_name / browser_name 需要一个 UA 解析器，而<b>项目里没有</b>：
--    hutool（带 UserAgentUtil）已于 2026-08-08 整体移除，现有 LoginService 只是
--    request.getHeader(USER_AGENT) 原样存。所以要自己写一个轻量解析放
--    sa-base/common/util，别为此把 hutool 引回来（交接文档「六条必知」#6）。
--    🔴 解析不出来时落 'UNKNOWN'，<b>不要落 NULL</b> —— 否则「各端占比」这类统计
--       会出现一个谁也说不清的黑洞，而 UNKNOWN 至少是个能追的信号。

-- ⚠️ 本表刻意没有 update_time：它是纯 append 的日志，行写完就不再改。
--    铁律 9 要求「新建表把这两列写全」针对的是<b>业务表</b>——
--    给一张永不更新的日志表挂 ON UPDATE，是给一个不存在的动作留位置。
--    铁律 9 的自查脚本只筛「有 update_time 却缺 ON UPDATE」的表，本表不在其列。


-- ============================================================================
-- 4. t_member_id_seq  会员号发号序列
-- ============================================================================
--
-- 只有一行。用它替代 AUTO_INCREMENT，理由见「附一」。
--
-- 【号段模式：一次批发 step 个，在 JVM 里零售】
--   这张表<b>只有一行</b>，是全库最典型的热点行 —— 每注册一个用户就打它一次的话，
--   所有注册请求会在这一行上排成一条串行队列，并发注册直接被它卡死。
--
--   所以不逐个取，一次取一整段：
--     UPDATE t_member_id_seq SET next_seq = LAST_INSERT_ID(next_seq + step) WHERE id = 1;
--     SELECT LAST_INSERT_ID();      -- 同连接内取回，无竞态。返回的是本段的【上界】
--   得到的段是 [返回值 - step, 返回值)，交给 JVM 内的 AtomicLong 零售。
--   step=1000 时，注册 1000 个人才打一次库 —— 热点行问题直接消失。
--
--   🔴 <b>重启会丢弃当前段没用完的号</b>，这是号段模式唯一的代价，而在这里
--      <b>完全无害</b>：对外的会员号经过 Feistel 置换（见附一），本来就是跳着走的，
--      内部序号 500 和 501 映射出来的会员号毫无关系。少用掉几百个内部序号，
--      在 90 亿的容量里连零头都算不上，外部更是一点都看不出来。
--
--   ✅ <b>多实例天然安全</b>：UPDATE 是原子的，两个实例并发执行会各拿各的段，
--      不可能重叠。不需要分布式锁。
--
--   ❌ <b>不要做双 buffer 预取</b>。那是百万级日注册量才需要的东西。
--      按日注册 1 万算，一天也只取 10 次段，每次几毫秒 —— 用尽时同步取一次
--      完全可以接受。上双 buffer 只是徒增一个需要维护的异步分支。
--
--   ⚠️ next_seq <b>只能前进，绝不能回退</b>。回退会重新发出已经用掉的号，
--      主键冲突还算好的，最坏是两个人共用一个会员号。
--      运维手工改这张表前先想清楚这一条。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_member_id_seq`;
CREATE TABLE `t_member_id_seq`
(
    `id`          tinyint NOT NULL DEFAULT 1 COMMENT '恒为1，本表只有一行',
    -- 语义是「水位」而非「下一个号」：已经批发出去的内部序号到此为止，
    -- 下一段从这里开始。不是会员号！经 Feistel 置换后才是会员号
    `next_seq`    bigint  NOT NULL DEFAULT 0 COMMENT '内部序号分配水位（已批发到此），只增不减',
    -- 走配置更灵活：低频环境调小减少重启浪费，高并发调大减少打库次数
    `step`        int     NOT NULL DEFAULT 1000 COMMENT '号段大小：一次批发多少个内部序号',
    `update_time` datetime         DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) COMMENT ='会员号发号序列（单行，号段模式）';

INSERT INTO `t_member_id_seq` (`id`, `next_seq`, `step`) VALUES (1, 0, 1000)
ON DUPLICATE KEY UPDATE `id` = `id`;


-- ============================================================================
-- 附一：会员号怎么发 —— 10位数字，不重复，且不泄露注册量
-- ============================================================================
--
-- 【为什么不能直接 AUTO_INCREMENT 从 1000000000 起】
--   会<b>泄露业务量</b>。竞对今天注册一个号、下月再注册一个，两个 ID 相减
--   就是你这一个月的真实新增用户数。B站早期的 mid 就是顺序的，正因为被人拿去
--   统计注册量，后来才改成跳号的。这不是理论风险。
--   顺带还有个问题：多环境/将来分库时自增会撞。
--
-- 【也不要「随机生成 + 查库判重」】
--   90亿空间下前期碰撞概率确实极低，但它要一个「生成→查库→撞了重来」的循环，
--   而这个循环在高并发下需要加锁才真正安全（两个请求同时查都说没撞）。
--   为了一件本可以纯计算解决的事引入锁，不划算。
--
-- 【做法：内部自增序号 + Feistel 可逆置换】
--   内部老老实实自增 0,1,2,3...（t_member_id_seq），
--   对外用一个<b>可逆的伪随机置换</b> F 把它打散。置换是<b>双射</b>，所以：
--     · 绝不重复（数学保证，不是概率保证）—— 不需要查库判重、不需要加锁
--     · 相邻序号的输出毫无规律 —— 看不出注册量，也猜不到别人的会员号
--     · 可逆 —— 拿会员号能反推内部序号，排查和对账时有用
--     · 纯计算，零 IO
--
--   实现（约30行，无外部依赖）：
--     1. 域取 2^34 ≈ 171.8亿，拆成左右各17位
--     2. 4 轮 Feistel，轮函数 f(round,right) = HMAC-SHA256(KEY, round||right) 取低17位
--        —— Feistel 结构的性质：<b>无论轮函数是什么，整体一定可逆</b>，
--           所以轮函数只要够乱就行，不需要自己是双射
--     3. cycle-walking 把域收窄到 90亿：输出 ≥ 9e9 就再套一次 F，直到落进范围
--     4. 加偏移 1000000000，得到 10 位会员号
--
--   实测（200万个号）：200万序号→200万个互不相同的会员号；20万次 decode(encode(x))==x；
--     区间 1000000660~9999996321 全部10位；平均 1.909 次迭代/号（理论 2^34/9e9=1.909）；
--     「连续同向最长游程」5 —— 同一测量下 java.util.Random 也是 5，顺序自增是 19999，
--     即在该指标上与真随机源无法区分。
--
--   🔴 KEY 一旦上线<b>永远不能改</b>。改了就是换了一个置换，
--      新号可能撞上已发出去的老号。KEY 放配置并纳入备份，像对待数据库密码一样对待它。
--
--   🔴 uk_mbr_name / 主键冲突是最后的兜底。数学上不该撞，但「数学上不该」和
--      「线上不会」之间隔着一个实现 bug。撞了就让它抛异常，<b>不要 catch 后重试</b>
--      —— 那会把「置换实现写错了」这种严重问题，悄悄退化成一个随机重试的隐患。
--
-- 【如果不在乎泄露注册量】
--   那 AUTO_INCREMENT 起步 1000000000 就够了，t_member_id_seq 和置换都可以不要。
--   决策点只有一个：<b>你的注册量是不是商业机密。</b>
--
-- 【为什么 10 位是够的】
--   9,000,000,000 个号。按日注册 10 万算能撑 246 年。
--   而且 10 位正好是手机号长度量级，客服问得清、用户报得出。
-- ============================================================================


-- ============================================================================
-- 附二：把九张已部署表的关联键从 member_name 迁到 member_id
-- ============================================================================
--
-- 需要迁的表（均为 `member_name varchar(64)`）：
--   t_member_wallet            t_member_asset_transaction   t_member_coupon
--   t_physical_delivery        t_proposal_record            t_task_record
--   t_prize_log                t_draw_prize_log             t_lottery_record
--
-- 商城四张（t_mall_order / t_mall_exchange_limit / t_mall_address / t_mall_favorite）
-- 还没上线，直接改字段定义即可，不需要迁移。
--
-- ----------------------------------------------------------------------------
-- 🟢 「迁到 member_id」≠「把 member_name 删掉」
-- ----------------------------------------------------------------------------
-- 当初各表放 member_name 的另一个理由是：<b>后台不用关联会员表就能认出是谁</b>。
-- 这个理由完全成立，而且不该为了换关联键就丢掉 —— 换个键不等于让数据变得看不懂。
--
-- 两件事拆开就都能要：
--     member_id    关联键。建索引、做外键、参与 join 与唯一约束
--     member_name  <b>展示快照</b>。只给人看，<b>不建索引</b>
--
-- 这和 t_mall_order 里已经在做的事是同一个模式：那张表冗余了 commodity_name /
-- sku_attrs / cover_file_id，理由是「商品改名改价后历史订单不能跟着变」。
-- 单据上记的账号，本来就该是<b>下单当时</b>那个账号，而不是这人现在叫什么 ——
-- 审计要回答的是「当时是谁」。所以这不是冗余，是快照，和商品名快照同源。
--
-- 【但不是每张表都该冗余，按「单据 or 状态」分】
--
--   ✅ 单据类（写完就不再改的历史记录）—— 冗余 member_name 快照：
--        t_member_asset_transaction   t_proposal_record   t_member_coupon
--        t_physical_delivery          t_prize_log         t_draw_prize_log
--        t_lottery_record             t_mall_order
--
--   ❌ 状态类（表达"当前是什么"，会被反复 UPDATE）—— 只留 member_id：
--        t_member_wallet（当前余额）      t_task_record（当前进度）
--        t_mall_exchange_limit（计数器）  t_mall_address   t_mall_favorite
--      这些表里放快照没有意义，还会和主表长期不一致 —— 用户改了名，
--      钱包表里却永远是老名字，反而更难认。后台查这几张表时 join t_member 即可
--      （主键 join，分页列表毫无压力）；嫌麻烦就建个 VIEW 给排查用。
--
-- 【为什么这个折中很便宜】
--   varchar 的成本大头在<b>索引</b>，不在行。快照列不建索引，
--   一行只多 12 字节左右（"zhangsan_01" 11字节 + 1字节长度前缀）。
--   而原方案是把 varchar(64) 塞进多条索引，每个索引项最多占到 258 字节。
--   所以「换键」省下的是索引，「留快照」花掉的是行 —— 差着一个数量级。
--
--   🔴 快照列<b>一定不要建索引</b>。这不只是省空间，更是一道防线：
--      没有索引，谁写了 `WHERE member_name = ?` 会立刻表现为慢查询被发现；
--      建了索引，它就会一直静默地"能用"，于是关联键又悄悄退回到了 member_name，
--      改名断链的问题原样复活。
--      快照列的定义统一写成 varchar(32)（对齐 t_member.member_name），
--      注释里写死「展示用快照，非关联键，不要用于查询」。
--
-- 【顺序很重要，反了会丢数据】
--   1. 先建 t_member 并为存量 member_name 各发一个 member_id（造数脚本）
--   2. 每张表 ADD COLUMN member_id bigint NULL（此时不加 NOT NULL，回填期允许为空）
--   3. 回填：UPDATE t_xxx x JOIN t_member m ON x.member_name = m.member_name
--              SET x.member_id = m.member_id;
--   4. 🔴 校验，通过后才能继续：
--        SELECT COUNT(*) FROM t_xxx WHERE member_id IS NULL;   -- 必须为 0
--      不为 0 说明有 member_name 在 t_member 里没有对应行（脏数据），
--      <b>先查清楚再往下走</b>，不要直接给个默认值糊过去。
--   5. 改索引：新建 member_id 版本的索引 → 删掉 member_name 版本
--   6. ALTER member_id 改 NOT NULL
--   7. 代码切到 member_id，观察一个版本
--   8. 收尾，按上面的「单据 or 状态」分类走两条路：
--      · 状态类 → DROP COLUMN member_name
--      · 单据类 → <b>保留该列作展示快照</b>，但要做三件事：
--          a. 删掉它身上所有索引（这是本次换键的主要收益所在）
--          b. MODIFY 成 varchar(32)，与 t_member.member_name 对齐
--          c. 改列注释为「展示用快照，非关联键，不要用于查询」
--        🔴 存量行的快照值就是现在的 member_name，天然正确，不用回填。
--           但<b>新写入的代码必须记得填它</b> —— 漏填不会报错，只会让新记录
--           在后台显示成空白，而且要等运营发现才知道。落地时把它放进
--           insert 的必填校验里。
--
-- ⚠️ 唯一索引要连带改，别只改普通索引。至少这几条含 member_name：
--     t_member_wallet.uk_member_asset(member_name, asset_type)
--     t_member_asset_transaction.idx_t_biz_mbr_ast_txn_time(member_name, asset_type, create_time)
--     t_task_record.uk_t_tsk_rec_mbr_cfg_prd(member_id, task_config_id, period_key)  ← 已是 member_id 语义，改类型即可
--
-- 💡 迁完的附带收益：member_id 是 bigint(8字节)，member_name 是 varchar(64)。
--    t_member_asset_transaction 是全库最大的表（每次积分变动一行），
--    它那条 (member_name, asset_type, create_time) 联合索引会显著变小。
-- ============================================================================


-- ============================================================================
-- 附三：member_name 规则（微信号风格）
-- ============================================================================
--
-- 【格式】正则 ^[A-Za-z][A-Za-z0-9_-]{5,19}$
--   · 字母开头，总长 6~20
--   · 允许字母、数字、下划线、减号
--   · <b>不能是纯数字</b> —— 否则和 member_id 混淆，客服问「你的账号是多少」会答错
--   · 大小写不敏感：ZhangSan 与 zhangsan 是同一个账号。
--     存储保留用户输入的原始大小写（用于展示），唯一性由 ci 排序规则保证
--
-- 【注册时自动生成，不强迫用户当场想】
--   微信的做法：先给一个 wxid_xxxxxxxx，用户想改再改。照抄即可 ——
--   注册流程里插一步「设置账号」是最典型的流失点。
--   生成建议 'm' + 9位随机小写字母数字，撞了重试（这里可以重试，
--   因为它不像 member_id 那样有双射保证，本来就是随机的）。
--
-- 【改名限频】
--   name_update_time 记上次修改时间，Service 里判 now() - name_update_time >= 1年。
--   🔴 判据用<b>数据库时钟</b>（铁律 9/10），不要用 JVM 时间。
--   为空表示从未改过，第一次改直接放行。
--
-- 【保留字】
--   admin / root / system / official / kefu / service 这类前缀要拦，
--   否则会出现 "admin_service" 这种冒充官方的账号。
--   放 Service 层的常量集合里，别进数据库 —— 需要随时加词。
--
-- 【为什么长度给到 varchar(32) 而不是刚好 20】
--   留头寸给将来可能的前缀/后缀迁移（比如合并库时给冲突方加后缀）。
--   varchar 按实际长度存，多留 12 个字符不占空间。
-- ============================================================================


-- ============================================================================
-- 附四：注销 —— 为什么没有 deleted_flag，以及两个标识的释放策略不同
-- ============================================================================
--
-- 会员<b>既不能物理删，也不该用 deleted_flag 软删</b>：
--   · 物理删：钱包、流水、订单、发货单全部悬空，账对不平，客诉无从查证。
--     而且账务流水依法要留存。
--   · deleted_flag：它表达的是「这行数据没了」，但注销用户的<b>数据必须还在</b>，
--     只是这个人不能再登录、不该再出现在运营的会员列表里。语义不对。
--
-- 正确做法是 `status = 3(已注销)`，注销时做四件事：
--   ① phone_hash / email_hash <b>置 NULL</b> —— 释放手机号，让本人能用同一号码重新注册。
--      MySQL 的 UNIQUE 允许多个 NULL，正是我们需要的行为。
--      phone 密文按合规要求决定清空还是保留（多数场景要清）。
--   ② nickname 改为脱敏占位，如 '已注销用户'。历史订单里仍能看到有这么个人，
--      但看不出是谁。
--   ③ member_name <b>不释放</b>，永久占用。
--      🔴 这一条和手机号刚好相反，别想当然套用：
--         手机号本来就会被运营商回收给别人，释放是符合现实的；
--         而 member_name 是<b>社交身份</b> —— 张三注销后李四拿到 "zhangsan"，
--         老朋友加过来会以为还是张三。这是社工诈骗的现成入口。
--         微信也是这么做的：注销后微信号不会给别人。
--   ④ member_id <b>永不回收</b>。回收会导致历史流水张冠李戴 ——
--      新用户拿到老号，一查订单是别人的。绝对不能省。
--
-- 🔴 这套必须现在就定。事后补做不了：存量已注销用户的状态无从回填，
--    而「哪些手机号已释放」更是彻底丢失。
-- ============================================================================


-- ============================================================================
-- 附六：hash 列怎么选 —— 省资源要省在编码上，不是省在位宽上
-- ============================================================================
--
-- 【实测】本机 MySQL 8.4.8 / 库排序规则 utf8mb4_0900_ai_ci，
--   建临时表挂唯一索引，读 information_schema.COLUMNS.CHARACTER_OCTET_LENGTH：
--
--     列类型                          索引键长(字节)
--     char(32)  utf8mb4  (MD5 hex)         128
--     char(64)  utf8mb4  (SHA-256 hex)     256      ← 本文件的初版，最浪费
--     char(64)  ascii    (SHA-256 hex)      64
--     char(128) utf8mb4  (SHA-512 hex)     512
--     binary(32)         (SHA-256 原始)     32      ← 采用
--
-- 【结论一：真正的浪费是 hex 编码 + utf8mb4，不是哈希位宽】
--   hex 把 32 字节摘要写成 64 个字符，先 ×2；utf8mb4 的 CHAR 按每字符最大 4 字节
--   计算索引键长，再 ×4 —— 合起来 <b>×8</b>。
--   摘要本来就是二进制，转成字符再存是纯粹的多此一举。binary(32) 就是它本来的样子。
--
-- 【结论二：char(32) 存 MD5「更省」是个错觉】
--   看列宽是 32 比 64 小一半，看实际索引键长是 128 vs 32 —— <b>比正确答案还大 4 倍</b>，
--   同时又把哈希换成了早已不该再用的 MD5。省了个寂寞还赔上算法。
--
-- 【结论三：char(128)/SHA-512 在这里纯浪费】
--   手机号只有约 19 亿个可能取值（1[3-9] + 9位）。安全瓶颈在<b>输入空间</b>，
--   不在摘要宽度 —— 把哈希从 256 位加宽到 512 位，一点也没让手机号变得更难猜，
--   只让索引大了一倍。
--
-- 【比位宽重要得多的事：算法要 HMAC，密钥要和库分开放】
--   正因为手机号只有 19 亿种，GPU 每秒能算几十亿次 SHA-256 ——
--   <b>「盐拿到了就等于全部明文」，几分钟的事。</b>所以：
--     · 用 HMAC-SHA256(KEY, 手机号) 而不是 SHA-256(手机号 + 盐)。
--       HMAC 是为「带密钥的哈希」设计的标准构造；攻击者不知道 KEY 就<b>根本无法枚举</b>，
--       只能去爆破 256 位密钥，那是不可能的。
--     · KEY 放配置中心/KMS，<b>不能和数据库备份躺在同一个地方</b> ——
--       否则一次拖库就把两样都拿走了，等于没加密。
--     · 🔴 不要给每个会员生成一个盐存进库 —— 对这三列不是「没必要」，是<b>根本不可行</b>：
--         ① 循环依赖：要算出 hash 才能定位到行，要定位到行才能取出那一行的盐。
--            真要用，只能全表扫一遍逐行试盐 —— 索引直接作废。
--         ② 唯一约束会失效：同一个手机号配不同的盐得到不同 hash，
--            uk_mbr_phone_hash <b>挡不住同号重复注册</b>，而这正是它存在的唯一理由。
--         这三列需要的性质是「同一输入恒得同一输出」，随机盐与之直接冲突。
--       per-row 盐想防的是「彩虹表 + 一次性批量破解所有哈希」，
--       而全局 HMAC 密钥把这件事防得更彻底：攻击者没有 KEY，<b>连一个候选 hash 都算不出来</b>，
--       根本轮不到查表。所以密钥保管到位，就不需要也不该再叠一层 per-row 盐。
--       （密码列是另一回事：Argon2 的盐已内嵌在 PHC 串里，见 t_member.password 的注释。）
--   国密场景换 HMAC-SM3，输出同样 32 字节，列定义一个字都不用改
--   （BouncyCastle 1.85 已在依赖里，项目也已有 SM 系实现 ApiEncryptServiceSmImpl）。
--
-- 【代价：实体字段是 byte[] 而不是 String】
--   MyBatis-Plus 映射 binary(32) → byte[]，查询条件传 byte[]，都正常工作。
--   排查时 `SELECT HEX(phone_hash)` 就能看到熟悉的十六进制。
--   如果嫌 byte[] 别扭、想保留 String 映射，退一步用
--   `char(64) CHARACTER SET ascii`（64 字节，仍比初版省 4 倍）—— 但没必要，
--   这两列应用层从不展示，只算和比，byte[] 反而更诚实。
--
-- 【不要再往下抠了】
--   有人会提「截断到 binary(16)」（取 SHA-256 前 128 位）。1000 万行下确实不会碰撞，
--   但省下的 16 字节远不如从 256→32 那一步值钱，却要永远回答「为什么是 16 字节」。
--   停在 binary(32)。
-- ============================================================================


-- ============================================================================
-- 附五：其它落地要点
-- ============================================================================
--
-- ① member_name / member_id 都<b>不可在 UpdateForm 里出现</b>（member_name 走独立的
--    改名接口并校验限频）。对齐铁律 8 对业务编码的做法：编码创建后不可改，
--    UpdateForm 本就不含它 —— 光靠前端禁用不够，直接 POST 照样能写进去（铁律 2）。
--
-- ② 时间列已按铁律 9 写全 DEFAULT CURRENT_TIMESTAMP / ON UPDATE。
--    实体上<b>不要</b>加 @TableField(fill = FieldFill.*)，加了会把 null 显式写进去，
--    覆盖掉 DDL 默认值（实测让整列 create_time 变 NULL）。
--    代码生成器的 Entity.java.vm 曾硬编码这两个注解，生成完记得检查。
--
-- ③ 头像走文件模块，保存时调
--    fileAssetService.confirm(List.of(avatarFileId), "MEMBER_AVATAR", memberId)。
--    头像和商品图同理，建议也开一个独立文件分类挡在素材库之外（见 mall.sql 的讨论）。
--
-- 自查（新建表后跑一次，铁律 9）：
--   SELECT table_name FROM information_schema.columns
--    WHERE table_schema='smart_admin_v3' AND column_name='update_time'
--      AND extra NOT LIKE '%on update%';
-- ============================================================================
