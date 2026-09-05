-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码。
SET NAMES utf8mb4;

-- ============================================================================
-- t_file_category 加 public_flag：给免登录读取端点补上那道闸
-- 2026-09-05
-- ============================================================================
--
-- 【补的是什么】
-- `/support/file/public/**` 是<b>免登录</b>端点，给 C 端与富文本里的 <img> 用。
-- 它此前只判「storageKey 对应的文件存不存在」，存在就吐字节 —— 也就是说
-- <b>任何一个有效的 storageKey，不带登录态就能下载</b>。
--
-- 配置文件（solvela-base.yaml）里当时写着「它会查 visibility，私有文件一律 404」，
-- 但 t_file 从来没有过 visibility 列，base_file 模块里也一处都没有。
-- 注释描述的行为不存在，那句注释这次一并改掉了。
--
-- 【今天没有泄露，这是给将来补的】
-- 现有 7 个分类（COMMON / NOTICE / HELP_DOC / FEEDBACK / ACTIVITY / CONTENT /
-- MALL_COMMODITY）全部是<b>运营上传的展示素材</b>，本来就打算给所有人看；
-- 唯一的上传入口也只有管理端的运营上传。t_member_verify 至今没有文件列。
--
-- 真正的风险在于<b>这条路上没有闸门</b>：第一个加「用户上传实名件 / 头像」的人，
-- 默认就是公开，而且没有任何东西会提醒他。等发现时，那些 key 已经散在
-- 富文本 HTML、接口响应、日志与 Referer 里了 —— 而 storageKey 永不变、
-- 端点还挂着 Cache-Control: immutable，等于一条永久有效的免登录链接。
--
-- 这和 PresignCapable 类注释里记的那次事故（「本地模式下私有文件根本不私有」）
-- 是同一类问题，只是换到了 public 端点上。
--
-- 【为什么默认 0（私有）】
-- 🔴 公开必须显式开口子。反过来写的话，新建分类忘了设置就是默默裸奔，
--    而那个方向的错误是数据泄露；设错成私有的表现只是「C 端图裂了」——
--    看得见、改一下就好。与 C 端路由「默认需登录、公开页显式标 anonymous」同一条取向。
--
-- 【为什么是 DB 列，而不是像 SYSTEM_CODES 那样写死在代码里】
-- FileCategoryService.SYSTEM_CODES 刻意留在代码里，理由是「引用关系本来就在代码侧」——
-- 那个名单回答的是「代码有没有硬编码引用这个分类」，确实只有代码知道。
-- 但 public_flag 回答的是「这个分类的文件该不该免登录可见」，
-- 这是运营<b>新建分类时</b>的业务决定；放代码里等于每加一个分类都要发版。
--
-- 【判断做在 SQL 里，不在 Java 里】
-- 新增 FileDao.selectPublicByStorageKey，把「分类是否公开」压进同一条 join。
-- 分成「先查文件、再查分类、再 if」三步的话，那个 if 是可以被忘掉的，
-- 而忘掉的表现是免登录端点吐出私有文件、不报任何错。
-- 顺带保住了「不给探测者任何区分信号」：不存在与不公开返回的都是 null，一律 404。
-- ----------------------------------------------------------------------------

ALTER TABLE `t_file_category`
    ADD COLUMN `public_flag` tinyint NOT NULL DEFAULT 0
        COMMENT '该分类下的文件是否免登录可读：0-否(默认), 1-是。公开要显式开口子' AFTER `sort`;

-- 存量 7 个分类全部是运营展示素材，一次性开公开。
-- 🔴 按 category_code 更新，不按 category_id —— dev 上是 6、prod 上可能是别的数。
UPDATE `t_file_category`
SET `public_flag` = 1
WHERE `category_code` IN
      ('COMMON', 'NOTICE', 'HELP_DOC', 'FEEDBACK', 'ACTIVITY', 'CONTENT', 'MALL_COMMODITY');

-- 自查：应当有 7 行 public_flag=1，且不该有第 8 行意外为 1
SELECT `category_code`, `category_name`, `public_flag`
FROM `t_file_category`
ORDER BY `public_flag` DESC, `sort`;
