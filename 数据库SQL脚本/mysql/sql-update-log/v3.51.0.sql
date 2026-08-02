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
