-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- 业务编码统一约定：活动编码 / 奖品编码一律为「10 位大写字母 + 数字」的随机组合（如 H88JHKJFNE），全局唯一。
-- 运营可手工输入也可点按钮生成，服务端按 SmartCodeUtil.BIZ_CODE_REGEX 校验格式并判重。

-- t_activity_config.activity_code 建表时已有 uk_act_code 唯一索引，无需变更。

-- t_prize_config.prize_code 原本只是普通列，跨活动可重复 —— 这会让 PrizeConfigService.getByPrizeCode()
-- 的 .one() 命中多行抛 TooManyResultsException，且与「编码全局唯一」的约定冲突，此处补唯一索引。
-- ⚠️ 若库中已有重复 prize_code，本语句会失败。先用下面这条排查并清理：
--   SELECT prize_code, COUNT(*) c FROM t_prize_config GROUP BY prize_code HAVING c > 1;
ALTER TABLE `t_prize_config`
    ADD UNIQUE KEY `uk_prize_code` (`prize_code`);
