-- =============================================================================
-- 生产升级：补齐网关限流与安全域（2026-10-18 一次性脚本）
-- =============================================================================
-- 为什么会有这个文件：
--   README 的前提是「本项目只有一套库，而且它永远是最新的」——改表就是直接改它、
--   然后重新导出基线。那个前提现在不成立了：生产跑在独立的 mysql-data 卷里，
--   而基线只在【数据卷为空】时由 initdb 执行一次，之后再也不会生效。
--   于是这套库停在了安全域开工之前。README「那『已有环境怎么升级』怎么办」一节
--   说的就是这种情况：按那一次的实际差异写一个升级脚本。这就是那一个。
--
-- 差异是怎么算出来的（不是手数的）：
--   在同一台 MySQL 上建临时库 _diff_probe，灌进 schema-baseline.sql + data-baseline.sql，
--   再拿 information_schema 和生产库逐列、逐索引比，种子表逐行按主键比。结论：
--     · 6 张表整张缺
--     · 1 个字段缺：t_member.last_active_time
--     · 7 张配置表共缺 104 行种子
--     · 85 张共有表的列定义与索引【零漂移】——所以这里没有任何 MODIFY COLUMN
--
-- 可重复执行：建表用 IF NOT EXISTS，加列走 information_schema 守卫，种子用 INSERT IGNORE。
--
-- 执行方式（在项目根目录）：
--   docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" solvela' \
--     < "数据库SQL脚本/mysql/生产升级-20261018-安全与限流域补齐.sql"
--
-- ⚠️ 跑之前先备份：docker compose exec -T mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --no-tablespaces solvela' > 备份.sql
-- =============================================================================


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `t_member_risk_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `rule_code` varchar(32) NOT NULL COMMENT '规则编码，与代码里 MemberRiskRule#code() 一一对应。启动时双向校验',
  `rule_name` varchar(64) NOT NULL COMMENT '给人看的名字',
  `event_type` tinyint NOT NULL COMMENT '适用事件：1-登录。见 MemberRiskEventTypeEnum',
  `phase` tinyint NOT NULL COMMENT '阶段：1-前置（验凭据之前）, 2-后置（验凭据之后）',
  `rule_type` tinyint NOT NULL COMMENT '类型：1-强规则（固定处置）, 2-强规则（分级 / 矩阵，处置写在 params）, 3-打分规则',
  `direct_decision` tinyint DEFAULT NULL COMMENT '固定处置：1-放行, 2-滑块, 3-OTP, 4-换通道OTP, 5-密码冷却, 6-限流, 7-拒绝。分级与打分规则为 NULL',
  `score` smallint NOT NULL DEFAULT '0' COMMENT '打分规则命中时加的分。强规则为 0',
  `rule_group` varchar(16) DEFAULT NULL COMMENT '互斥组：同组命中只取最高分（如 GEO）。NULL 表示独立证据，照常相加',
  `mode` tinyint NOT NULL DEFAULT '1' COMMENT '档位：0-关闭, 1-陪跑（只记录）, 2-生效。GLOBAL_CAPTCHA 的开关就是它',
  `bypass_by_trust` tinyint NOT NULL DEFAULT '1' COMMENT '可信设备是否跳过：1-跳过, 0-照常执行（强规则、前置规则、不可能旅行为 0）',
  `params` json DEFAULT NULL COMMENT '参数，形状由各规则声明并在保存时校验。分级规则的 tiers[].do 取值同 direct_decision',
  `description` varchar(256) DEFAULT NULL COMMENT '它在抓什么、为什么是这个阈值',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mbr_risk_rule_code` (`rule_code`)
) ENGINE=InnoDB AUTO_INCREMENT=36 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员风控规则（逻辑在代码，参数与档位在表）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `t_rate_limit_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `target_type` tinyint NOT NULL COMMENT '1-场景（默认值）, 2-接口（覆盖）',
  `target` varchar(128) NOT NULL COMMENT '场景编码（与 RateLimitScene 枚举一一对应）；或接口键，如 "POST /activity/{activityCode}/draw"，必须与网关上报的路由清单逐字一致',
  `dimension` tinyint NOT NULL COMMENT '0-全局（不分人，保护下游）, 1-IP, 2-账号, 3-设备, 4-IP段（只允许四个匿名场景）',
  `burst` int NOT NULL COMMENT '突发：10 秒令牌桶容量',
  `limit_3m` int NOT NULL COMMENT '3 分钟阈值',
  `limit_5m` int NOT NULL COMMENT '5 分钟阈值',
  `limit_10m` int NOT NULL COMMENT '10 分钟阈值',
  `long_window` varchar(8) DEFAULT NULL COMMENT '可选长窗口：1h / 1d。NULL = 不启用（从登录风控迁过来的几条用它）',
  `limit_long` int DEFAULT NULL COMMENT '长窗口阈值。long_window 为 NULL 时为 NULL',
  `mode` tinyint NOT NULL DEFAULT '2' COMMENT '档位：0-关闭, 1-陪跑（只记日志）, 2-生效',
  `description` varchar(256) DEFAULT NULL COMMENT '为什么是这个数；接口行写清为什么要和场景不一样',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rl_rule` (`target_type`,`target`,`dimension`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='网关限流规则：场景给默认值，接口按需覆盖；后台维护，经 Redis 快照下发到网关';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `t_security_list` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `list_type` tinyint NOT NULL COMMENT '名单类型：1-黑, 2-灰, 3-白',
  `dimension` tinyint NOT NULL COMMENT '维度：1-IP, 2-IP段（CIDR）, 3-身份摘要, 4-会员',
  `list_value` varchar(64) NOT NULL COMMENT 'IP / CIDR 原文；身份摘要存 HEX(PiiHasher 输出)，不存明文；会员存会员号',
  `score` smallint DEFAULT NULL COMMENT '灰名单命中加的分。黑、白名单为 NULL',
  `reason` varchar(128) NOT NULL COMMENT '为什么进名单：给下一个看到它的人的人话',
  `source` tinyint NOT NULL DEFAULT '1' COMMENT '来源：1-人工, 2-自动',
  `source_rule` varchar(32) DEFAULT NULL COMMENT '自动入名单的来源：HONEYPOT / CANARY / KEYWORD / RATE_LIMIT / ESCALATE（黑到期转灰） / SEGMENT（同段连坐）。人工条目为 NULL',
  `hit_count` int NOT NULL DEFAULT '1' COMMENT '有效期内命中次数：自动条目重复命中时累加',
  `last_hit_time` datetime DEFAULT NULL COMMENT '最近一次命中（人工条目为 NULL）',
  `last_incident_id` bigint DEFAULT NULL COMMENT '最近一次证据，指向 t_security_incident',
  `blocked_count` bigint NOT NULL DEFAULT '0' COMMENT '封禁期间被安全闸直接挡掉的请求数，定时从 Redis 计数器同步',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1-有效, 0-已撤销。撤销不删行，留痕',
  `expire_time` datetime DEFAULT NULL COMMENT '到期时间。NULL 表示长期；后台新增默认 30 天；自动条目必填',
  `operator` varchar(64) NOT NULL COMMENT '新增人；自动条目为 system',
  `revoke_operator` varchar(64) DEFAULT NULL COMMENT '撤销人：status=0 时必填',
  `revoke_reason` varchar(128) DEFAULT NULL COMMENT '撤销原因：status=0 时必填',
  `revoke_time` datetime DEFAULT NULL COMMENT '撤销时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sec_list` (`dimension`,`list_value`,`list_type`),
  KEY `idx_sec_list_active` (`status`,`expire_time`),
  KEY `idx_sec_list_rule` (`source_rule`,`last_hit_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全名单（网关与登录风控共用）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `t_security_incident` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `incident_type` tinyint NOT NULL COMMENT '1-蜜罐接口, 2-蜜罐账号, 3-关键字, 4-限流判罚 / 升档, 5-未知字段（低置信，只记录）',
  `rule_code` varchar(32) DEFAULT NULL COMMENT '命中的蜜罐路径 / 关键字规则 / 限流场景编码',
  `action` tinyint NOT NULL COMMENT '实际处置：0-仅记录, 1-拉灰, 2-拉黑',
  `suppress_reason` tinyint NOT NULL DEFAULT '0' COMMENT '处罚被压下的原因：0-无, 1-跨站发起, 2-熔断中, 3-老设备豁免（仅 IP 部分）',
  `penalty_level` tinyint DEFAULT NULL COMMENT '限流判罚的档位（1-6），其它类型为 NULL',
  `client_ip` varchar(39) DEFAULT NULL COMMENT '客户端IP',
  `device_id` char(32) DEFAULT NULL COMMENT '设备号',
  `member_id` bigint DEFAULT NULL COMMENT '会员号（已登录时）',
  `identity_hash` binary(32) DEFAULT NULL COMMENT '蜜罐账号命中时的登录身份摘要',
  `http_method` varchar(8) DEFAULT NULL COMMENT '请求方法',
  `request_path` varchar(256) DEFAULT NULL COMMENT '请求路径，不含查询串',
  `matched_field` tinyint DEFAULT NULL COMMENT '命中位置：1-UA, 2-路径, 3-查询参数, 4-请求体字段',
  `matched_param` varchar(32) DEFAULT NULL COMMENT '命中的参数 / 字段名',
  `matched_sample` varchar(128) DEFAULT NULL COMMENT '命中片段，截断。攻击者可控：后台只能按纯文本展示',
  `user_agent` varchar(256) DEFAULT NULL COMMENT 'UA，截断。攻击者可控：后台只能按纯文本展示',
  `penalty_until` datetime DEFAULT NULL COMMENT '处罚到期时间',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '全链路追踪ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (`id`,`create_time`),
  KEY `idx_sec_inc_ip` (`client_ip`,`create_time`),
  KEY `idx_sec_inc_device` (`device_id`,`create_time`),
  KEY `idx_sec_inc_member` (`member_id`,`create_time`),
  KEY `idx_sec_inc_type` (`incident_type`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全事件流水（append-only，按月分区，保留 6 个月；只在状态变化时写）'
/*!50500 PARTITION BY RANGE  COLUMNS(create_time)
(PARTITION p202610 VALUES LESS THAN ('2026-11-01') ENGINE = InnoDB,
 PARTITION p202611 VALUES LESS THAN ('2026-12-01') ENGINE = InnoDB,
 PARTITION p202612 VALUES LESS THAN ('2027-01-01') ENGINE = InnoDB,
 PARTITION p202701 VALUES LESS THAN ('2027-02-01') ENGINE = InnoDB,
 PARTITION pmax VALUES LESS THAN (MAXVALUE) ENGINE = InnoDB) */;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `t_member_risk_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `event_type` tinyint NOT NULL COMMENT '事件：1-登录, 2-登录二次验证。见 MemberRiskEventTypeEnum',
  `ref_event_id` bigint DEFAULT NULL COMMENT '关联事件：二次验证指向签票的那次登录',
  `member_id` bigint DEFAULT NULL COMMENT '会员号。可空：查无此人时没有',
  `identity_hash` binary(32) DEFAULT NULL COMMENT '登录身份的 PiiHasher 摘要，与 t_member.phone_hash/email_hash 同算法',
  `login_type` tinyint DEFAULT NULL COMMENT '登录方式：见 MemberLoginType',
  `device_id` char(32) DEFAULT NULL COMMENT '设备号，可空（没带设备令牌）',
  `device_trust` tinyint DEFAULT NULL COMMENT '设备信任：0-无设备, 1-新设备, 2-老交情, 3-验证过, 4-注册设备。查无此人时为 NULL',
  `ip_familiar` tinyint DEFAULT NULL COMMENT 'IP 熟悉度：0-陌生, 1-宽松熟悉, 2-严格熟悉。查无此人时为 NULL',
  `dormant` tinyint DEFAULT NULL COMMENT '是否沉睡：0-活跃, 1-沉睡。查无此人时为 NULL',
  `client_ip` varchar(39) DEFAULT NULL COMMENT '客户端IP',
  `ip_region` varchar(64) DEFAULT NULL COMMENT 'IP归属地（ip2region，与登录日志同格式，仅供展示）',
  `captcha_passed` tinyint NOT NULL DEFAULT '0' COMMENT '本次请求是否带了有效滑块通行票',
  `pre_score` smallint NOT NULL DEFAULT '0' COMMENT '前置得分（只算生效规则）',
  `post_score` smallint NOT NULL DEFAULT '0' COMMENT '后置得分（只算生效规则，不含前置分）',
  `shadow_score` smallint NOT NULL DEFAULT '0' COMMENT '陪跑总分（生效 + 陪跑规则，前置 + 后置）',
  `decision` tinyint NOT NULL COMMENT '生效结论：1-放行, 2-滑块, 3-OTP, 4-换通道OTP, 5-密码冷却, 6-限流, 7-拒绝',
  `shadow_decision` tinyint NOT NULL COMMENT '陪跑结论：假如所有陪跑规则都生效',
  `verify_channel` tinyint DEFAULT NULL COMMENT '核身通道：1-邮箱, 2-短信。decision 为 OTP 时有值',
  `outcome` tinyint NOT NULL COMMENT '最终结局：1-成功, 2-凭据错, 3-查无此人, 4-冻结, 5-冷却/锁定中, 6-格式非法, 7-需要滑块, 8-前置限流/拒绝, 9-后置限流/拒绝, 10-已签凭票, 11-二次验证通过, 12-二次验证失败, 13-内部异常（end 没被调用，close 兜底落库）',
  `label` tinyint DEFAULT NULL COMMENT '事后标注：1-确认本人, 2-确认被盗。只这三列允许事后更新',
  `label_source` tinyint DEFAULT NULL COMMENT '标注来源：1-用户「这不是我」, 2-老设备上点「是我」, 3-客服',
  `label_time` datetime DEFAULT NULL COMMENT '标注时间',
  `cost_ms` smallint DEFAULT NULL COMMENT '风控本身的耗时（毫秒），不含验密码',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '全链路追踪ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (`id`,`create_time`),
  KEY `idx_mbr_risk_evt_member` (`member_id`,`create_time`),
  KEY `idx_mbr_risk_evt_identity` (`identity_hash`,`create_time`),
  KEY `idx_mbr_risk_evt_ip` (`client_ip`,`create_time`),
  KEY `idx_mbr_risk_evt_device` (`device_id`,`create_time`),
  KEY `idx_mbr_risk_evt_decision` (`decision`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员风控决策流水（按月分区，除标注三列外 append-only）'
/*!50500 PARTITION BY RANGE  COLUMNS(create_time)
(PARTITION p202610 VALUES LESS THAN ('2026-11-01') ENGINE = InnoDB,
 PARTITION p202611 VALUES LESS THAN ('2026-12-01') ENGINE = InnoDB,
 PARTITION p202612 VALUES LESS THAN ('2027-01-01') ENGINE = InnoDB,
 PARTITION p202701 VALUES LESS THAN ('2027-02-01') ENGINE = InnoDB,
 PARTITION pmax VALUES LESS THAN (MAXVALUE) ENGINE = InnoDB) */;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `t_member_risk_hit` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `event_id` bigint NOT NULL COMMENT '决策流水 id',
  `rule_code` varchar(32) NOT NULL COMMENT '规则编码',
  `mode` tinyint NOT NULL COMMENT '命中时规则所处的档位：1-陪跑, 2-生效。规则档位会变，这里存当时的',
  `decision` tinyint DEFAULT NULL COMMENT '这条规则给出的处置（强规则 / 矩阵）。打分规则为 NULL',
  `score` smallint NOT NULL DEFAULT '0' COMMENT '命中时加的分（强规则为 0）',
  `detail` varchar(128) DEFAULT NULL COMMENT '命中依据，给人看：如「新设备 + 陌生IP」「连续错误第 4 次，冷却 5 分钟」',
  `error` tinyint NOT NULL DEFAULT '0' COMMENT '1 = 特征取不到、按未命中处理',
  `create_time` datetime NOT NULL COMMENT '写入时显式赋成决策流水的 create_time（不用 DEFAULT）：两表按 (id, create_time) 关联才能裁剪分区',
  PRIMARY KEY (`id`,`create_time`),
  KEY `idx_mbr_risk_hit_event` (`event_id`,`create_time`),
  KEY `idx_mbr_risk_hit_rule` (`rule_code`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员风控规则命中明细（只记命中，按月分区）'
/*!50500 PARTITION BY RANGE  COLUMNS(create_time)
(PARTITION p202610 VALUES LESS THAN ('2026-11-01') ENGINE = InnoDB,
 PARTITION p202611 VALUES LESS THAN ('2026-12-01') ENGINE = InnoDB,
 PARTITION p202612 VALUES LESS THAN ('2027-01-01') ENGINE = InnoDB,
 PARTITION p202701 VALUES LESS THAN ('2027-02-01') ENGINE = InnoDB,
 PARTITION pmax VALUES LESS THAN (MAXVALUE) ENGINE = InnoDB) */;
/*!40101 SET character_set_client = @saved_cs_client */;

-- -----------------------------------------------------------------------------
-- ② t_member 补字段（守卫式，重复执行无副作用）
-- -----------------------------------------------------------------------------
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `t_member` ADD COLUMN `last_active_time` datetime DEFAULT NULL COMMENT ''最后活跃时间：注册时写入，之后网关按小时节流写。登录风控判「沉睡」用它，不用最后登录时间（会话 30 天不续期）'' AFTER `name_update_time`',
    'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_member' AND column_name = 'last_active_time');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- ③ 种子数据：104 行
--    t_member_risk_rule 19（缺了 app-biz 启动就失败：规则表与代码双向校验）
--    t_rate_limit_rule  28（网关限流场景默认值与接口覆盖）
--    t_menu             23（menu_id 626-648：安全中心一级菜单 636 及其子页，权限码 security:*）
--    t_role_menu        23（role_menu_id 1301-1323：全部授给 role_id=1）
--    t_solvela_job       5（按月分区维护、网关快照重发、设备封禁镜像、安全事件、名单维护）
--    t_notification_template 3（CONTACT_BOUND / LEAKED_PASSWORD / SUSPICIOUS_LOGIN）
--    t_config            2（gateway_rate_limit / gateway_security_gate）
--    t_mail_template     1（member_suspicious_login）
--    主键无一撞车：库里 max(menu_id)=625、max(role_menu_id)=1300，全部是追加。
-- -----------------------------------------------------------------------------
INSERT  IGNORE INTO `t_member_risk_rule` (`id`, `rule_code`, `rule_name`, `event_type`, `phase`, `rule_type`, `direct_decision`, `score`, `rule_group`, `mode`, `bypass_by_trust`, `params`, `description`, `create_time`, `update_time`) VALUES (1,'GLOBAL_CAPTCHA','全局滑块',1,1,1,2,0,NULL,2,0,NULL,'开着时所有密码登录都要滑块，熟悉网络免滑块不起作用。上线初期开着（= 改造前的行为），之后作为应急档','2026-10-14 23:19:28','2026-10-17 19:57:42');
INSERT  IGNORE INTO `t_member_risk_rule` (`id`, `rule_code`, `rule_name`, `event_type`, `phase`, `rule_type`, `direct_decision`, `score`, `rule_group`, `mode`, `bypass_by_trust`, `params`, `description`, `create_time`, `update_time`) VALUES (4,'IP_FAIL_LIMIT','IP 登录失败',1,1,2,NULL,0,NULL,2,0,'{\"tiers\": [{\"do\": 2, \"over\": 5}, {\"do\": 6, \"over\": 20}], \"window\": \"1h\"}','单 IP 每小时登录失败。20 迁自 LoginIpGuard；5 的滑块档在全局滑块关掉后才起作用','2026-10-14 23:19:28','2026-10-17 19:57:35');
INSERT  IGNORE INTO `t_member_risk_rule` (`id`, `rule_code`, `rule_name`, `event_type`, `phase`, `rule_type`, `direct_decision`, `score`, `rule_group`, `mode`, `bypass_by_trust`, `params`, `description`, `create_time`, `update_time`) VALUES (5,'IP_UNKNOWN_LIMIT','IP 试不存在账号',1,1,2,NULL,0,NULL,2,0,'{\"tiers\": [{\"do\": 2, \"over\": 3}, {\"do\": 6, \"over\": 10}], \"window\": \"1h\"}','单 IP 每小时尝试不存在的账号。信号最强的一条：真人偶尔记错邮箱，一小时试十个只可能是脚本','2026-10-14 23:19:28','2026-10-17 19:57:33');
INSERT  IGNORE INTO `t_member_risk_rule` (`id`, `rule_code`, `rule_name`, `event_type`, `phase`, `rule_type`, `direct_decision`, `score`, `rule_group`, `mode`, `bypass_by_trust`, `params`, `description`, `create_time`, `update_time`) VALUES (6,'IP_ACCOUNT_FANOUT','IP 尝试的不同账号数',1,1,2,NULL,0,NULL,1,0,'{\"tiers\": [{\"do\": 2, \"over\": 5}, {\"do\": 6, \"over\": 30}], \"window\": \"1h\"}','单 IP 每小时尝试过的不同账号（不论存在与否）。撞库特征；出口 NAT 天然账号多，所以第一档是滑块','2026-10-14 23:19:28','2026-10-14 23:19:28');
INSERT  IGNORE INTO `t_member_risk_rule` (`id`, `rule_code`, `rule_name`, `event_type`, `phase`, `rule_type`, `direct_decision`, `score`, `rule_group`, `mode`, `bypass_by_trust`, `params`, `description`, `create_time`, `update_time`) VALUES (9,'DEVICE_FAIL_LIMIT','设备登录失败',1,1,2,NULL,0,NULL,1,0,'{\"tiers\": [{\"do\": 6, \"over\": 10}], \"window\": \"1h\", \"observe\": true}','单设备每小时登录失败，命中生效时进观察档。迁自 DeviceGuard（原 dry-run）','2026-10-14 23:19:28','2026-10-14 23:19:28');
INSERT  IGNORE INTO `t_member_risk_rule` (`id`, `rule_code`, `rule_name`, `event_type`, `phase`, `rule_type`, `direct_decision`, `score`, `rule_group`, `mode`, `bypass_by_trust`, `params`, `description`, `create_time`, `update_time`) VALUES (10,'PWD_COOLDOWN','密码阶梯冷却',1,1,2,NULL,0,NULL,2,0,'{\"free\": 2, \"steps\": [\"60s\", \"5m\", \"10m\", \"60m\", \"24h\"], \"idleReset\": \"24h\", \"recordFrom\": \"60m\"}','按身份摘要计数（防枚举），可信设备单独一桶（防恶意锁人），只锁密码通道。取代旧账号锁','2026-10-14 23:19:28','2026-10-17 19:57:33');
INSERT  IGNORE INTO `t_member_risk_rule` (`id`, `rule_code`, `rule_name`, `event_type`, `phase`, `rule_type`, `direct_decision`, `score`, `rule_group`, `mode`, `bypass_by_trust`, `params`, `description`, `create_time`, `update_time`) VALUES (11,'CAPTCHA_MATRIX','前置矩阵（熟悉网络免滑块）',1,1,2,NULL,0,NULL,2,0,'{\"v4Prefix\": 24, \"v6Prefix\": 64, \"recentIps\": 10, \"lookbackDays\": 90}','默认要滑块；账号存在且 IP 与最近成功登录同网段时放宽。只能放宽不能收紧。全局滑块开着时无效果','2026-10-14 23:19:28','2026-10-17 19:57:42');
INSERT  IGNORE INTO `t_member_risk_rule` (`id`, `rule_code`, `rule_name`, `event_type`, `phase`, `rule_type`, `direct_decision`, `score`, `rule_group`, `mode`, `bypass_by_trust`, `params`, `description`, `create_time`, `update_time`) VALUES (12,'NO_DEVICE','没带设备令牌',1,1,3,NULL,10,NULL,1,0,NULL,'老客户端或直接打接口的脚本。弱信号','2026-10-14 23:19:28','2026-10-17 19:57:34');
INSERT  IGNORE INTO `t_member_risk_rule` (`id`, `rule_code`, `rule_name`, `event_type`, `phase`, `rule_type`, `direct_decision`, `score`, `rule_group`, `mode`, `bypass_by_trust`, `params`, `description`, `create_time`, `update_time`) VALUES (13,'IP_DATACENTER','机房 IP',1,1,3,NULL,30,NULL,1,0,'{\"keywords\": [\"阿里云\", \"腾讯云\", \"华为云\", \"百度云\", \"京东云\", \"Amazon\", \"Google\", \"Microsoft\", \"DigitalOcean\"]}','ip2region 的 ISP 段命中机房关键字。关键字级，覆盖不全','2026-10-14 23:19:28','2026-10-14 23:19:28');
INSERT  IGNORE INTO `t_member_risk_rule` (`id`, `rule_code`, `rule_name`, `event_type`, `phase`, `rule_type`, `direct_decision`, `score`, `rule_group`, `mode`, `bypass_by_trust`, `params`, `description`, `create_time`, `update_time`) VALUES (14,'IP_GREYLIST','IP 灰名单',1,1,3,NULL,0,NULL,1,0,NULL,'IP / IP 段在灰名单，加名单条目上设的分','2026-10-14 23:19:28','2026-10-14 23:19:28');
INSERT  IGNORE INTO `t_member_risk_rule` (`id`, `rule_code`, `rule_name`, `event_type`, `phase`, `rule_type`, `direct_decision`, `score`, `rule_group`, `mode`, `bypass_by_trust`, `params`, `description`, `create_time`, `update_time`) VALUES (15,'DEVICE_OBSERVE','设备观察档',1,2,1,3,0,NULL,2,0,NULL,'设备处在观察档，登录要多验一道码。迁自 checkDeviceChallenge','2026-10-14 23:19:28','2026-10-14 23:19:28');
INSERT  IGNORE INTO `t_member_risk_rule` (`id`, `rule_code`, `rule_name`, `event_type`, `phase`, `rule_type`, `direct_decision`, `score`, `rule_group`, `mode`, `bypass_by_trust`, `params`, `description`, `create_time`, `update_time`) VALUES (16,'DEVICE_FANOUT','一机多号',1,2,2,NULL,0,NULL,1,0,'{\"tiers\": [{\"do\": 6, \"over\": 3}], \"window\": \"24h\", \"observe\": true, \"sliding\": true}','单设备最后一次活动起 24h 内碰过的不同账号。养号、买号的最强信号。迁自 DeviceGuard（原 dry-run）','2026-10-14 23:19:28','2026-10-14 23:19:28');
INSERT  IGNORE INTO `t_member_risk_rule` (`id`, `rule_code`, `rule_name`, `event_type`, `phase`, `rule_type`, `direct_decision`, `score`, `rule_group`, `mode`, `bypass_by_trust`, `params`, `description`, `create_time`, `update_time`) VALUES (17,'LOGIN_MATRIX','后置矩阵',1,2,2,NULL,0,NULL,2,0,'{\"cells\": {\"TRUSTED\": 1, \"NEW_UNFAMILIAR\": 3, \"NEW_FAMILIAR_ACTIVE\": 1, \"NEW_FAMILIAR_DORMANT\": 3}, \"dormantDays\": 30}','设备信任 × IP 熟悉度（宽松口径）× 活跃度。可信设备放行；新设备遇到陌生网络或沉睡回来要 OTP','2026-10-14 23:19:28','2026-10-17 19:57:42');
INSERT  IGNORE INTO `t_member_risk_rule` (`id`, `rule_code`, `rule_name`, `event_type`, `phase`, `rule_type`, `direct_decision`, `score`, `rule_group`, `mode`, `bypass_by_trust`, `params`, `description`, `create_time`, `update_time`) VALUES (18,'NEW_COUNTRY','新国家',1,2,3,NULL,50,'GEO',1,1,'{\"minSuccess\": 3, \"lookbackDays\": 90}','国家不在近 90 天成功登录过的国家里。成功登录少于 3 次不判','2026-10-14 23:19:28','2026-10-14 23:19:28');
INSERT  IGNORE INTO `t_member_risk_rule` (`id`, `rule_code`, `rule_name`, `event_type`, `phase`, `rule_type`, `direct_decision`, `score`, `rule_group`, `mode`, `bypass_by_trust`, `params`, `description`, `create_time`, `update_time`) VALUES (19,'IMPOSSIBLE_TRAVEL','不可能旅行',1,2,3,NULL,40,'GEO',1,0,'{\"crossCountryMinutes\": 120, \"crossProvinceMinutes\": 30}','与上次成功登录跨国且 < 2h，或跨省且 < 30min。可信设备也不跳过：唯一能抓老设备令牌被复制的规则','2026-10-14 23:19:28','2026-10-14 23:19:28');
INSERT  IGNORE INTO `t_member_risk_rule` (`id`, `rule_code`, `rule_name`, `event_type`, `phase`, `rule_type`, `direct_decision`, `score`, `rule_group`, `mode`, `bypass_by_trust`, `params`, `description`, `create_time`, `update_time`) VALUES (20,'ACCOUNT_FAIL_BURST','账号失败爆发',1,2,3,NULL,40,NULL,1,1,'{\"minIps\": 2, \"window\": \"1h\", \"minFails\": 3}','本账号近 1h 失败 ≥ 3 次且来自 ≥ 2 个 IP。补旧账号锁的盲区：只数次数、不数来源','2026-10-14 23:19:28','2026-10-14 23:19:28');
INSERT  IGNORE INTO `t_member_risk_rule` (`id`, `rule_code`, `rule_name`, `event_type`, `phase`, `rule_type`, `direct_decision`, `score`, `rule_group`, `mode`, `bypass_by_trust`, `params`, `description`, `create_time`, `update_time`) VALUES (31,'ASSET_DEVICE_FANOUT','资产出口-一机多号',5,1,2,NULL,0,NULL,2,0,'{\"tiers\": [{\"do\": 3, \"over\": 3}], \"window\": \"1d\"}','一台设备一天替几个不同账号搬东西。登录侧的碰号数数不到这件事（买号的人每个号只登一次）。给 OTP 档所以敢直接生效：误判一次只是多收一封信','2026-10-16 20:00:00','2026-10-16 20:00:00');
INSERT  IGNORE INTO `t_member_risk_rule` (`id`, `rule_code`, `rule_name`, `event_type`, `phase`, `rule_type`, `direct_decision`, `score`, `rule_group`, `mode`, `bypass_by_trust`, `params`, `description`, `create_time`, `update_time`) VALUES (34,'BIND_DEVICE_FANOUT','换绑-一机多号',6,1,2,NULL,0,NULL,1,0,'{\"tiers\": [{\"do\": 6, \"over\": 2}], \"window\": \"1d\"}','一台设备一天替几个账号换联系方式。换完原主连找回密码都走不通，只剩 7 天冷静期在挡。新拍的阈值 + 硬拦处置，所以先陪跑','2026-10-16 21:00:00','2026-10-16 21:00:00');
INSERT  IGNORE INTO `t_member_risk_rule` (`id`, `rule_code`, `rule_name`, `event_type`, `phase`, `rule_type`, `direct_decision`, `score`, `rule_group`, `mode`, `bypass_by_trust`, `params`, `description`, `create_time`, `update_time`) VALUES (35,'CANARY_ACCOUNT','蜜罐账号',1,1,1,1,0,NULL,2,0,NULL,'登录的是蜜罐账号（register_source = CANARY）→ IP 与设备罚 24 小时、投安全事件、告警「用户名单可能已泄露」。固定处置是放行：不改变登录的回答，登录照常在密码比对失败，与真实账号输错密码逐字节相同。蜜罐账号由后台接口建，邮箱不进代码与种子。见 docs/架构/网关阶梯限流-技术方案.md §5.1','2026-10-17 09:36:22','2026-10-17 09:36:22');
INSERT  IGNORE INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES (1,1,'LOGIN',2,5,10,15,20,NULL,NULL,2,'同一登录身份。匿名场景：处罚封顶 30 分钟，老设备豁免（方案 §2.4）','2026-10-17 12:00:00','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES (2,1,'LOGIN',3,5,10,15,20,'1d',30,2,'单设备。长窗口迁自登录风控 DEVICE_LOGIN_LIMIT（1 天 30 次）','2026-10-17 12:00:00','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES (3,1,'LOGIN',1,10,30,45,60,'1h',60,2,'单 IP。长窗口迁自登录风控 IP_ATTEMPT_LIMIT（1 小时 60 次）','2026-10-17 12:00:00','2026-10-17 19:57:45');
INSERT  IGNORE INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES (4,1,'LOGIN',4,50,150,225,300,NULL,NULL,2,'IP 段（/24、/48）：约为 IP 维度的 5 倍，处罚封顶 30 分钟（方案 §2.5）','2026-10-17 12:00:00','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES (5,1,'REGISTER',2,2,3,4,5,NULL,NULL,2,'同一注册身份','2026-10-17 12:00:00','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES (6,1,'REGISTER',3,2,3,4,5,'1d',2,2,'单设备。长窗口迁自登录风控 REG_DEVICE_LIMIT（1 天 2 个）','2026-10-17 12:00:00','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES (7,1,'REGISTER',1,3,6,8,10,'1h',10,2,'单 IP。长窗口迁自登录风控 REG_IP_LIMIT（1 小时 10 次）','2026-10-17 12:00:00','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES (8,1,'REGISTER',4,15,30,40,50,NULL,NULL,2,'IP 段：约为 IP 维度的 5 倍','2026-10-17 12:00:00','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES (9,1,'CODE_SEND',2,2,4,5,6,NULL,NULL,2,'同一收码目标 / 会员。发码的日配额仍在业务域（VerificationCodeProperties）','2026-10-17 12:00:00','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES (10,1,'CODE_SEND',3,2,4,5,6,NULL,NULL,2,'单设备','2026-10-17 12:00:00','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES (11,1,'CODE_SEND',1,3,8,12,15,NULL,NULL,2,'单 IP','2026-10-17 12:00:00','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES (12,1,'CODE_SEND',4,15,40,60,75,NULL,NULL,2,'IP 段：约为 IP 维度的 5 倍','2026-10-17 12:00:00','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES (13,1,'PASSWORD_RESET',2,3,8,10,12,NULL,NULL,2,'同一重置身份','2026-10-17 12:00:00','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES (14,1,'PASSWORD_RESET',3,3,8,10,12,'1d',5,2,'单设备。长窗口迁自登录风控 RESET_DEVICE_LIMIT（1 天 5 次）','2026-10-17 12:00:00','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES (15,1,'PASSWORD_RESET',1,5,15,20,30,'1h',20,2,'单 IP。长窗口迁自登录风控 RESET_IP_LIMIT（1 小时 20 次）','2026-10-17 12:00:00','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES (16,1,'PASSWORD_RESET',4,25,75,100,150,NULL,NULL,2,'IP 段：约为 IP 维度的 5 倍','2026-10-17 12:00:00','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES (17,1,'REDEEM',2,3,10,15,20,NULL,NULL,2,'会员。资产流出：兑换、支付、充值、领奖','2026-10-17 12:00:00','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES (18,1,'REDEEM',1,10,40,60,100,NULL,NULL,2,'单 IP','2026-10-17 12:00:00','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES (19,1,'SIGN',2,3,5,6,8,NULL,NULL,2,'会员。签到一天本来就只成功一次','2026-10-17 12:00:00','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES (20,1,'SIGN',1,20,100,150,200,NULL,NULL,2,'单 IP','2026-10-17 12:00:00','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES (21,1,'CAPTCHA',3,5,20,30,40,NULL,NULL,2,'单设备：出图 + 判题','2026-10-17 12:00:00','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES (22,1,'CAPTCHA',1,10,40,60,80,NULL,NULL,2,'单 IP。取代 CaptchaService 里原来的同 IP 每分钟出图 20 张','2026-10-17 12:00:00','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES (23,1,'DEFAULT_WRITE',2,10,100,150,200,NULL,NULL,2,'其余写接口：会员','2026-10-17 12:00:00','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES (24,1,'DEFAULT_WRITE',3,10,100,150,200,NULL,NULL,2,'其余写接口：设备','2026-10-17 12:00:00','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES (25,1,'DEFAULT_WRITE',1,30,500,750,1000,NULL,NULL,2,'其余写接口：IP','2026-10-17 12:00:00','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES (26,1,'DEFAULT_READ',2,20,300,450,600,NULL,NULL,2,'其余读接口：会员。阈值很宽，只挡爬虫级别的量','2026-10-17 12:00:00','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES (27,1,'DEFAULT_READ',3,20,300,450,600,NULL,NULL,2,'其余读接口：设备','2026-10-17 12:00:00','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_rate_limit_rule` (`id`, `target_type`, `target`, `dimension`, `burst`, `limit_3m`, `limit_5m`, `limit_10m`, `long_window`, `limit_long`, `mode`, `description`, `create_time`, `update_time`) VALUES (28,1,'DEFAULT_READ',1,50,1000,1500,2000,NULL,NULL,2,'其余读接口：IP','2026-10-17 12:00:00','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`, `create_time`, `update_user_id`, `update_time`) VALUES (626,'风控规则',2,636,2,'/security/risk-rule','/business/security/risk-rule-list.vue',1,NULL,NULL,'SafetyOutlined',NULL,0,NULL,0,1,0,0,1,'2026-10-15 16:39:12',NULL,'2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`, `create_time`, `update_user_id`, `update_time`) VALUES (627,'查询',3,626,1,NULL,NULL,1,'security:query','security:query',NULL,NULL,0,NULL,0,1,0,0,1,'2026-10-15 16:39:12',NULL,'2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`, `create_time`, `update_user_id`, `update_time`) VALUES (628,'改档位 / 分值 / 参数',3,626,2,NULL,NULL,1,'security:rule','security:rule',NULL,NULL,0,NULL,0,1,0,0,1,'2026-10-15 16:39:12',NULL,'2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`, `create_time`, `update_user_id`, `update_time`) VALUES (629,'风控决策流水',2,636,6,'/security/risk-event','/business/security/risk-event-list.vue',1,NULL,NULL,'FileSearchOutlined',NULL,0,NULL,0,1,0,0,1,'2026-10-15 16:39:12',NULL,'2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`, `create_time`, `update_user_id`, `update_time`) VALUES (630,'查询',3,629,1,NULL,NULL,1,'security:query','security:query',NULL,NULL,0,NULL,0,1,0,0,1,'2026-10-15 16:39:12',NULL,'2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`, `create_time`, `update_user_id`, `update_time`) VALUES (631,'安全名单',2,636,5,'/security/list','/business/security/security-list-list.vue',1,NULL,NULL,NULL,NULL,0,NULL,0,1,0,0,1,'2026-10-15 23:35:17',NULL,'2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`, `create_time`, `update_user_id`, `update_time`) VALUES (632,'查询',3,631,1,NULL,NULL,1,'security:query','security:query',NULL,NULL,0,NULL,0,1,0,0,1,'2026-10-15 23:35:17',NULL,'2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`, `create_time`, `update_user_id`, `update_time`) VALUES (633,'新增 / 撤销',3,631,2,NULL,NULL,1,'security:list','security:list',NULL,NULL,0,NULL,0,1,0,0,1,'2026-10-15 23:35:17',NULL,'2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`, `create_time`, `update_user_id`, `update_time`) VALUES (634,'风控看板',2,636,8,'/security/stat','/business/security/risk-stat.vue',1,NULL,NULL,NULL,NULL,0,NULL,0,1,0,0,1,'2026-10-15 23:48:59',NULL,'2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`, `create_time`, `update_user_id`, `update_time`) VALUES (635,'查询',3,634,1,NULL,NULL,1,'security:query','security:query',NULL,NULL,0,NULL,0,1,0,0,1,'2026-10-15 23:48:59',NULL,'2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`, `create_time`, `update_user_id`, `update_time`) VALUES (636,'安全中心',1,0,1,NULL,NULL,1,NULL,NULL,'SafetyCertificateOutlined',NULL,0,NULL,0,1,0,0,1,'2026-10-17 11:48:31',NULL,'2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`, `create_time`, `update_user_id`, `update_time`) VALUES (637,'限流规则',2,636,1,'/security/rate-limit','/business/security/rate-limit-rule.vue',1,NULL,NULL,'DashboardOutlined',NULL,0,NULL,0,1,0,0,1,'2026-10-17 11:48:31',NULL,'2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`, `create_time`, `update_user_id`, `update_time`) VALUES (638,'查询',3,637,1,NULL,NULL,1,'security:query','security:query',NULL,NULL,0,NULL,0,1,0,0,1,'2026-10-17 11:48:31',NULL,'2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`, `create_time`, `update_user_id`, `update_time`) VALUES (639,'改阈值 / 接口覆盖',3,637,2,NULL,NULL,1,'security:rule','security:rule',NULL,NULL,0,NULL,0,1,0,0,1,'2026-10-17 11:48:31',NULL,'2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`, `create_time`, `update_user_id`, `update_time`) VALUES (640,'处罚查询',2,636,3,'/security/penalty','/business/security/penalty-query.vue',1,NULL,NULL,'StopOutlined',NULL,0,NULL,0,1,0,0,1,'2026-10-17 11:48:31',NULL,'2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`, `create_time`, `update_user_id`, `update_time`) VALUES (641,'查询',3,640,1,NULL,NULL,1,'security:query','security:query',NULL,NULL,0,NULL,0,1,0,0,1,'2026-10-17 11:48:31',NULL,'2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`, `create_time`, `update_user_id`, `update_time`) VALUES (642,'解除处罚',3,640,2,NULL,NULL,1,'security:penalty','security:penalty',NULL,NULL,0,NULL,0,1,0,0,1,'2026-10-17 11:48:31',NULL,'2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`, `create_time`, `update_user_id`, `update_time`) VALUES (643,'蜜罐与关键字',2,636,4,'/security/gate','/business/security/security-gate.vue',1,NULL,NULL,'BugOutlined',NULL,0,NULL,0,1,0,0,1,'2026-10-17 11:48:31',NULL,'2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`, `create_time`, `update_user_id`, `update_time`) VALUES (644,'查询',3,643,1,NULL,NULL,1,'security:query','security:query',NULL,NULL,0,NULL,0,1,0,0,1,'2026-10-17 11:48:31',NULL,'2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`, `create_time`, `update_user_id`, `update_time`) VALUES (645,'增删 / 停用',3,643,2,NULL,NULL,1,'security:rule','security:rule',NULL,NULL,0,NULL,0,1,0,0,1,'2026-10-17 11:48:31',NULL,'2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`, `create_time`, `update_user_id`, `update_time`) VALUES (646,'建蜜罐账号',3,643,3,NULL,NULL,1,'security:list','security:list',NULL,NULL,0,NULL,0,1,0,0,1,'2026-10-17 11:48:31',NULL,'2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`, `create_time`, `update_user_id`, `update_time`) VALUES (647,'安全事件',2,636,7,'/security/incident','/business/security/security-incident-list.vue',1,NULL,NULL,'AlertOutlined',NULL,0,NULL,0,1,0,0,1,'2026-10-17 11:48:31',NULL,'2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`, `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`, `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`, `create_user_id`, `create_time`, `update_user_id`, `update_time`) VALUES (648,'查询',3,647,1,NULL,NULL,1,'security:query','security:query',NULL,NULL,0,NULL,0,1,0,0,1,'2026-10-17 11:48:31',NULL,'2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_role_menu` (`role_menu_id`, `role_id`, `menu_id`, `update_time`, `create_time`) VALUES (1301,1,626,'2026-10-15 16:39:12','2026-10-15 16:39:12');
INSERT  IGNORE INTO `t_role_menu` (`role_menu_id`, `role_id`, `menu_id`, `update_time`, `create_time`) VALUES (1302,1,627,'2026-10-15 16:39:12','2026-10-15 16:39:12');
INSERT  IGNORE INTO `t_role_menu` (`role_menu_id`, `role_id`, `menu_id`, `update_time`, `create_time`) VALUES (1303,1,628,'2026-10-15 16:39:12','2026-10-15 16:39:12');
INSERT  IGNORE INTO `t_role_menu` (`role_menu_id`, `role_id`, `menu_id`, `update_time`, `create_time`) VALUES (1304,1,629,'2026-10-15 16:39:12','2026-10-15 16:39:12');
INSERT  IGNORE INTO `t_role_menu` (`role_menu_id`, `role_id`, `menu_id`, `update_time`, `create_time`) VALUES (1305,1,630,'2026-10-15 16:39:12','2026-10-15 16:39:12');
INSERT  IGNORE INTO `t_role_menu` (`role_menu_id`, `role_id`, `menu_id`, `update_time`, `create_time`) VALUES (1306,1,631,'2026-10-15 23:35:28','2026-10-15 23:35:28');
INSERT  IGNORE INTO `t_role_menu` (`role_menu_id`, `role_id`, `menu_id`, `update_time`, `create_time`) VALUES (1307,1,632,'2026-10-15 23:35:28','2026-10-15 23:35:28');
INSERT  IGNORE INTO `t_role_menu` (`role_menu_id`, `role_id`, `menu_id`, `update_time`, `create_time`) VALUES (1308,1,633,'2026-10-15 23:35:28','2026-10-15 23:35:28');
INSERT  IGNORE INTO `t_role_menu` (`role_menu_id`, `role_id`, `menu_id`, `update_time`, `create_time`) VALUES (1309,1,634,'2026-10-15 23:48:59','2026-10-15 23:48:59');
INSERT  IGNORE INTO `t_role_menu` (`role_menu_id`, `role_id`, `menu_id`, `update_time`, `create_time`) VALUES (1310,1,635,'2026-10-15 23:48:59','2026-10-15 23:48:59');
INSERT  IGNORE INTO `t_role_menu` (`role_menu_id`, `role_id`, `menu_id`, `update_time`, `create_time`) VALUES (1311,1,636,'2026-10-17 11:48:31','2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_role_menu` (`role_menu_id`, `role_id`, `menu_id`, `update_time`, `create_time`) VALUES (1312,1,637,'2026-10-17 11:48:31','2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_role_menu` (`role_menu_id`, `role_id`, `menu_id`, `update_time`, `create_time`) VALUES (1313,1,638,'2026-10-17 11:48:31','2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_role_menu` (`role_menu_id`, `role_id`, `menu_id`, `update_time`, `create_time`) VALUES (1314,1,639,'2026-10-17 11:48:31','2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_role_menu` (`role_menu_id`, `role_id`, `menu_id`, `update_time`, `create_time`) VALUES (1315,1,640,'2026-10-17 11:48:31','2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_role_menu` (`role_menu_id`, `role_id`, `menu_id`, `update_time`, `create_time`) VALUES (1316,1,641,'2026-10-17 11:48:31','2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_role_menu` (`role_menu_id`, `role_id`, `menu_id`, `update_time`, `create_time`) VALUES (1317,1,642,'2026-10-17 11:48:31','2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_role_menu` (`role_menu_id`, `role_id`, `menu_id`, `update_time`, `create_time`) VALUES (1318,1,643,'2026-10-17 11:48:31','2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_role_menu` (`role_menu_id`, `role_id`, `menu_id`, `update_time`, `create_time`) VALUES (1319,1,644,'2026-10-17 11:48:31','2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_role_menu` (`role_menu_id`, `role_id`, `menu_id`, `update_time`, `create_time`) VALUES (1320,1,645,'2026-10-17 11:48:31','2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_role_menu` (`role_menu_id`, `role_id`, `menu_id`, `update_time`, `create_time`) VALUES (1321,1,646,'2026-10-17 11:48:31','2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_role_menu` (`role_menu_id`, `role_id`, `menu_id`, `update_time`, `create_time`) VALUES (1322,1,647,'2026-10-17 11:48:31','2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_role_menu` (`role_menu_id`, `role_id`, `menu_id`, `update_time`, `create_time`) VALUES (1323,1,648,'2026-10-17 11:48:31','2026-10-17 11:48:31');
INSERT  IGNORE INTO `t_solvela_job` (`job_id`, `job_code`, `job_name`, `handler_name`, `job_group`, `trigger_type`, `trigger_value`, `next_trigger_time`, `prev_trigger_time`, `trigger_version`, `jitter_seconds`, `enabled_flag`, `param`, `preset_code`, `timeout_seconds`, `retry_times`, `retry_interval`, `misfire_strategy`, `misfire_threshold_sec`, `block_strategy`, `last_execute_time`, `last_execute_log_id`, `sort`, `remark`, `deleted_flag`, `update_name`, `create_time`, `update_time`, `app_env`, `alarm_receiver`, `continuous_fail_count`, `handler_missing_flag`, `terminal_flag`, `owner_biz_type`, `owner_biz_code`, `source`, `manual_modified_flag`) VALUES (28,'JOBPARTMNT','【基础】按月分区维护','partitionMaintain','SYSTEM','cron','0 50 3 * * *','2026-10-18 03:50:28','2026-10-17 21:26:33',1,60,1,NULL,'NORMAL',600,1,60,'SKIP',300,'DISCARD','2026-10-17 21:26:33',47260,0,'每天 03:50 给按月分区的表补下 2 个月的分区、删掉保留期（6 个月）之外的。🔴 不跑的后果是静默的：四张表（登录日志、风控决策流水与命中明细、安全事件流水）的分区只建到 2027-01，之后数据全落进 pmax，而它永不过期 —— 保留期形同虚设，而且之后只能靠 DELETE 清（长事务、主从延迟、碎片）。单次最多删 3 个分区，保留期误配时不会一口气抹掉几年数据。',0,'管理员','2026-10-15 16:17:37','2026-10-17 21:26:33','dev',NULL,0,0,0,'SYSTEM',NULL,'SYSTEM',0);
INSERT  IGNORE INTO `t_solvela_job` (`job_id`, `job_code`, `job_name`, `handler_name`, `job_group`, `trigger_type`, `trigger_value`, `next_trigger_time`, `prev_trigger_time`, `trigger_version`, `jitter_seconds`, `enabled_flag`, `param`, `preset_code`, `timeout_seconds`, `retry_times`, `retry_interval`, `misfire_strategy`, `misfire_threshold_sec`, `block_strategy`, `last_execute_time`, `last_execute_log_id`, `sort`, `remark`, `deleted_flag`, `update_name`, `create_time`, `update_time`, `app_env`, `alarm_receiver`, `continuous_fail_count`, `handler_missing_flag`, `terminal_flag`, `owner_biz_type`, `owner_biz_code`, `source`, `manual_modified_flag`) VALUES (29,'JOBGWSNAPS','【安全】网关规则快照重发','gatewaySnapshotPublish','SYSTEM','cron','0 1/5 * * * *','2026-10-17 21:41:09','2026-10-17 21:36:09',3,10,1,NULL,'NORMAL',30,0,60,'SKIP',300,'DISCARD','2026-10-17 21:36:09',47287,0,'每 5 分钟把限流规则、两条全局参数、IP 名单拼成快照重发到 Redis（gw:rl:snapshot）。主路径是后台保存后立即发布，本任务是自愈：Redis 被清、被内存淘汰、保存时发布失败，都在这里补回来。内容没变时版本不变，网关不会重载。见 docs/架构/网关阶梯限流-技术方案.md §6',0,'管理员','2026-10-17 12:00:00','2026-10-17 21:36:09','dev',NULL,0,0,0,'SYSTEM',NULL,'SYSTEM',0);
INSERT  IGNORE INTO `t_solvela_job` (`job_id`, `job_code`, `job_name`, `handler_name`, `job_group`, `trigger_type`, `trigger_value`, `next_trigger_time`, `prev_trigger_time`, `trigger_version`, `jitter_seconds`, `enabled_flag`, `param`, `preset_code`, `timeout_seconds`, `retry_times`, `retry_interval`, `misfire_strategy`, `misfire_threshold_sec`, `block_strategy`, `last_execute_time`, `last_execute_log_id`, `sort`, `remark`, `deleted_flag`, `update_name`, `create_time`, `update_time`, `app_env`, `alarm_receiver`, `continuous_fail_count`, `handler_missing_flag`, `terminal_flag`, `owner_biz_type`, `owner_biz_code`, `source`, `manual_modified_flag`) VALUES (30,'JOBDEVBANM','【安全】设备封禁镜像重建','deviceBanMirrorRebuild','SYSTEM','cron','0 3/5 * * * *','2026-10-17 21:43:00','2026-10-17 21:38:00',4,10,1,NULL,'NORMAL',30,0,60,'SKIP',300,'DISCARD','2026-10-17 21:38:00',47293,0,'每 5 分钟按 t_device.status 全量重建 Redis 里的设备封禁镜像（sec:dev:banned），网关安全闸靠它挡被封的设备。主路径是后台人工处置时立即同步，本任务是自愈：同步失败、Redis 被清或被内存淘汰，都在这里补回来。🔴 镜像丢了的后果是静默的：被封的设备照常能用。见 docs/架构/网关阶梯限流-技术方案.md §5.5',0,'管理员','2026-10-17 12:00:00','2026-10-17 21:38:00','dev',NULL,0,0,0,'SYSTEM',NULL,'SYSTEM',0);
INSERT  IGNORE INTO `t_solvela_job` (`job_id`, `job_code`, `job_name`, `handler_name`, `job_group`, `trigger_type`, `trigger_value`, `next_trigger_time`, `prev_trigger_time`, `trigger_version`, `jitter_seconds`, `enabled_flag`, `param`, `preset_code`, `timeout_seconds`, `retry_times`, `retry_interval`, `misfire_strategy`, `misfire_threshold_sec`, `block_strategy`, `last_execute_time`, `last_execute_log_id`, `sort`, `remark`, `deleted_flag`, `update_name`, `create_time`, `update_time`, `app_env`, `alarm_receiver`, `continuous_fail_count`, `handler_missing_flag`, `terminal_flag`, `owner_biz_type`, `owner_biz_code`, `source`, `manual_modified_flag`) VALUES (31,'JOBSECINCD','【安全】安全事件落库','securityIncidentDrain','SYSTEM','cron','30 * * * * *','2026-10-17 21:40:31','2026-10-17 21:39:31',14,5,1,NULL,'NORMAL',30,0,60,'SKIP',300,'DISCARD','2026-10-17 21:39:31',47295,0,'每分钟把 Redis 队列 sec:incident 里的安全事件（蜜罐、关键字、限流判罚）写进 t_security_incident，并按事件里的处置自动拉黑 / 拉灰 IP、把设备推进观察档；名单变了立即重发网关快照。取走的事件先挪到 sec:incident:processing，落完库才删，任务崩了下一轮接着做。处罚本身早已在 Redis 里生效，这个任务停了只影响留痕与名单。见 docs/架构/网关阶梯限流-技术方案.md §6.4',0,'管理员','2026-10-17 12:00:00','2026-10-17 21:39:31','dev',NULL,0,0,0,'SYSTEM',NULL,'SYSTEM',0);
INSERT  IGNORE INTO `t_solvela_job` (`job_id`, `job_code`, `job_name`, `handler_name`, `job_group`, `trigger_type`, `trigger_value`, `next_trigger_time`, `prev_trigger_time`, `trigger_version`, `jitter_seconds`, `enabled_flag`, `param`, `preset_code`, `timeout_seconds`, `retry_times`, `retry_interval`, `misfire_strategy`, `misfire_threshold_sec`, `block_strategy`, `last_execute_time`, `last_execute_log_id`, `sort`, `remark`, `deleted_flag`, `update_name`, `create_time`, `update_time`, `app_env`, `alarm_receiver`, `continuous_fail_count`, `handler_missing_flag`, `terminal_flag`, `owner_biz_type`, `owner_biz_code`, `source`, `manual_modified_flag`) VALUES (32,'JOBSECLIST','【安全】安全名单维护','securityListMaintain','SYSTEM','cron','0 2/5 * * * *','2026-10-17 21:42:02','2026-10-17 21:37:02',4,10,1,NULL,'NORMAL',30,0,60,'SKIP',300,'DISCARD','2026-10-17 21:37:02',47290,0,'每 5 分钟：到期的自动黑名单转灰 30 天（ESCALATE）；24 小时内同一 /24（IPv6 /48）有 3 个以上 IP 被自动拉黑则整段拉灰 7 天（SEGMENT，只拉灰、从不自动拉黑整段）；把网关攒在 Redis 的被挡次数（sec:blocked:*）加到黑名单的 blocked_count。参数在 t_config.gateway_security_gate。见 docs/架构/网关阶梯限流-技术方案.md §5.5、§6.3',0,'管理员','2026-10-17 12:00:00','2026-10-17 21:37:02','dev',NULL,0,0,0,'SYSTEM',NULL,'SYSTEM',0);
INSERT  IGNORE INTO `t_notification_template` (`template_code`, `version`, `category`, `title_template`, `content_template`, `param_keys`, `status`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES ('CONTACT_BOUND',1,'SYSTEM','账号绑定变更提醒','你的账号刚刚绑定了${contactType} ${maskedContact}。如果不是你本人操作，请立即修改密码，并在「我的登录设备」中下线其他设备。','[\"contactType\", \"maskedContact\"]',1,'system','2026-10-16 10:00:00',NULL,'2026-10-15 23:38:07');
INSERT  IGNORE INTO `t_notification_template` (`template_code`, `version`, `category`, `title_template`, `content_template`, `param_keys`, `status`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES ('LEAKED_PASSWORD',1,'SYSTEM','你的登录密码存在风险','你当前使用的登录密码出现在公开泄露的密码库中，这意味着它很可能已经被收录进撞库字典。你的账号目前没有发现异常，但建议你尽快把密码改成一个没有在别处用过的。我们不会在任何提醒里放链接，请自行打开 App 的「账号与安全」修改密码。','[]',1,'seed','2026-10-17 10:00:00',NULL,'2026-10-16 20:26:42');
INSERT  IGNORE INTO `t_notification_template` (`template_code`, `version`, `category`, `title_template`, `content_template`, `param_keys`, `status`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES ('SUSPICIOUS_LOGIN',1,'SYSTEM','可疑登录已被拦下','你的账号于 ${loginTime} 在 ${location} 的 ${deviceType} 上有一次可疑的登录尝试，我们没有让它直接进去。能走到这一步说明对方知道你的密码，建议你立即修改密码，并在「我的登录设备」中下线其他设备。','[\"loginTime\", \"deviceType\", \"location\"]',1,'system','2026-10-16 00:24:00',NULL,'2026-10-16 00:24:00');
INSERT  IGNORE INTO `t_config` (`config_id`, `config_name`, `config_key`, `config_value`, `remark`, `update_time`, `create_time`) VALUES (3,'网关限流','gateway_rate_limit','{\"ladder\":[\"60s\",\"5m\",\"30m\",\"1h\",\"2h\",\"8h\"],\"levelResetAfter\":\"24h\",\"anonAccountCap\":\"30m\",\"ipSegmentCap\":\"30m\",\"oldDeviceDays\":7,\"greyFactor\":0.5,\"rateLimitGreyLevel\":4,\"rateLimitGreyDays\":7}','阶梯处罚、清零时间、匿名账号维度与 IP 段维度的封顶、老设备豁免天数、灰名单阈值系数、限流重罚转灰。见 docs/架构/网关阶梯限流-技术方案.md §4、§5.5','2026-10-17 12:00:00','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_config` (`config_id`, `config_name`, `config_key`, `config_value`, `remark`, `update_time`, `create_time`) VALUES (4,'网关安全闸','gateway_security_gate','{\"ban\":\"24h\",\"escalateGreyDays\":30,\"segmentGrey\":{\"minIps\":3,\"within\":\"24h\",\"days\":7},\"breaker\":{\"window\":\"10m\",\"distinctIps\":50},\"honeypotPaths\":[{\"code\":\"ENV\",\"path\":\"/.env\",\"enabled\":true},{\"code\":\"GIT\",\"path\":\"/.git/**\",\"enabled\":true},{\"code\":\"ACTUATOR\",\"path\":\"/actuator/**\",\"enabled\":true},{\"code\":\"WP_LOGIN\",\"path\":\"/wp-login.php\",\"enabled\":true},{\"code\":\"WP_ADMIN\",\"path\":\"/wp-admin/**\",\"enabled\":true},{\"code\":\"PHPMYADMIN\",\"path\":\"/phpmyadmin/**\",\"enabled\":true},{\"code\":\"TOMCAT_MANAGER\",\"path\":\"/manager/html\",\"enabled\":true}],\"keywords\":[{\"code\":\"SQL_SLEEP\",\"scope\":\"PARAM\",\"pattern\":\"\\\\b(?:pg_)?sleep\\\\s*\\\\(\\\\s*\\\\d+(?:\\\\.\\\\d+)?\\\\s*\\\\)\",\"enabled\":true},{\"code\":\"SQL_BENCHMARK\",\"scope\":\"PARAM\",\"pattern\":\"\\\\bbenchmark\\\\s*\\\\(\\\\s*\\\\d+\\\\s*,\",\"enabled\":true},{\"code\":\"SQL_WAITFOR\",\"scope\":\"PARAM\",\"pattern\":\"\\\\bwaitfor\\\\s+delay\\\\s+\'\",\"enabled\":true},{\"code\":\"SQL_UNION_SELECT\",\"scope\":\"PARAM\",\"pattern\":\"\\\\bunion\\\\b[\\\\s/*]+(?:all[\\\\s/*]+)?select\\\\b[\\\\s/*]+(?:\\\\d|null\\\\b|@@|\\\\w+\\\\s*\\\\()\",\"enabled\":true},{\"code\":\"SQL_TAUTOLOGY\",\"scope\":\"PARAM\",\"pattern\":\"\'\\\\s*(?:or|and)\\\\s+\'?\\\\d+\'?\\\\s*=\\\\s*\'?\\\\d+\",\"enabled\":true},{\"code\":\"SQL_OR_PAREN\",\"scope\":\"PARAM\",\"pattern\":\"\\\\b(?:or|and)\\\\s*\\\\(\\\\s*\\\\d+\\\\s*=\\\\s*\\\\d+\\\\s*\\\\)\",\"enabled\":true},{\"code\":\"SQL_INFO_SCHEMA\",\"scope\":\"PARAM\",\"pattern\":\"\\\\binformation_schema\\\\s*\\\\.\",\"enabled\":true},{\"code\":\"XSS_SCRIPT\",\"scope\":\"PARAM\",\"pattern\":\"<\\\\s*script\\\\b\",\"enabled\":true},{\"code\":\"XSS_EVENT\",\"scope\":\"PARAM\",\"pattern\":\"<[^>]*\\\\bon(?:error|load)\\\\s*=\",\"enabled\":true},{\"code\":\"PATH_TRAVERSAL\",\"scope\":\"PARAM\",\"pattern\":\"(?:\\\\.\\\\.[/\\\\\\\\]){2,}\",\"enabled\":true},{\"code\":\"FILE_PROBE\",\"scope\":\"PARAM\",\"pattern\":\"/etc/(?:passwd|shadow)\\\\b\",\"enabled\":true},{\"code\":\"JNDI\",\"scope\":\"PARAM\",\"pattern\":\"\\\\$\\\\{\\\\s*jndi\\\\s*:\",\"enabled\":true},{\"code\":\"UA_SCANNER\",\"scope\":\"UA\",\"pattern\":\"\\\\b(?:sqlmap|nikto|nmap|masscan|zgrab|nuclei|acunetix|wpscan|dirbuster|gobuster|ffuf)\\\\b\",\"enabled\":true}]}','蜜罐与关键字的封禁时长、黑到期转灰、同段连坐、熔断阈值。honeypotPaths 是扫描器才会探的路径（真实客户端永远不调，与真实接口重叠时网关拒绝启动）；keywords 是探测特征（scope=PARAM 扫路径、查询串、JSON 请求体的值，密码与验证码字段不扫；scope=UA 只扫 User-Agent），保存时每条都要过反例集（KeywordRules.NORMAL_INPUTS），命中任何一条正常输入就存不进去。每条都能单独停用。见 docs/架构/网关阶梯限流-技术方案.md §5','2026-10-17 19:57:45','2026-10-17 12:00:00');
INSERT  IGNORE INTO `t_mail_template` (`template_code`, `template_subject`, `template_content`, `template_type`, `disable_flag`, `update_time`, `create_time`) VALUES ('member_suspicious_login','可疑登录已被拦下','<!DOCTYPE HTML><html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/></head><body>\n<div style=\"margin:0 auto;width:690px;font-family:Helvetica,Arial,sans-serif;line-height:28px;\">\n  <h2>可疑登录已被拦下</h2>\n  <p>你的账号刚刚有一次<b>可疑的登录尝试</b>，我们没有让它直接进去：</p>\n  <p>时间：${loginTime}<br/>设备：${deviceType}<br/>地点：${location}</p>\n  <p style=\"color:#b00;\"><b>能走到这一步，说明对方知道你的密码。</b>请立即打开 App 修改密码，并在「我的登录设备」中点「下线其他设备」。</p>\n  <p>如果这就是你本人（比如换了网络或设备），按页面提示完成验证即可，无需其它操作。</p>\n  <p style=\"color:#888;font-size:13px;\">这封信里没有任何链接。收到带链接、要你点击登录或验证的「安全提醒」，那是钓鱼邮件。</p>\n</div></body></html>','freemarker',0,'2026-10-16 00:24:00','2026-10-16 00:24:00');
/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

