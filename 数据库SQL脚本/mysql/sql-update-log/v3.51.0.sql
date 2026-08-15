-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- ============================================================
-- v3.51.0  编辑器由 monaco-editor 换成 CodeMirror 6，菜单路径跟着改名
--
-- 背景：前端已把 monaco 相关的目录、路由、依赖全部清理，
--       路由 /monaco-editor 改为 /code-editor。
--       t_menu 里 id=301「在线编辑器」这条记录的 path 指向旧路由，
--       不改的话该菜单点开会 404。
--
-- ⚠️ 该菜单的 component 字段（/business/monaco-editor/JsonEditor.vue）本就是
--    失效值 —— buildRoutes 是按 ../views/<component> 解析的，而这个组件在
--    src/components 下，从来没被解析到过；页面实际由 routers.js 的静态路由提供。
--    这里一并把它改成新路径，保持记录自洽。
-- ============================================================

UPDATE `t_menu`
SET `path`      = '/code-editor',
    `component` = '/business/code-editor/JsonEditor.vue'
WHERE `menu_id` = 301
  AND `path` = '/monaco-editor';
