<!--
  * 首页 —— 已整体改为「营销作战大屏」
  *
  * 原来的首页是用户信息头 + 公告/通知 + 公众号卡片 + 更新日志 + 待办，
  * 对本项目（营销中台）没有信息价值；真正有用的只有活动参与统计那两张图。
  * 故直接把首页让给大屏，参与统计图作为其中一块保留下来。
  *
  * 口径与交互见 dashboard/marketing-dashboard.vue 的头注释，
  * 以及 docs/营销中台-数据统计方案.md、docs/活动大屏原型.html（v2.1）。
  *
  * ⚠️ 数据来自 /marketingStat 的七个接口，权限串统一为 marketingStat:query，
  *    功能点见 数据库SQL脚本/mysql/sql-update-log/v3.52.0.sql —— 没执行的话
  *    非超管账号打开首页会整片「没有权限」。
  *
  * @Author:    1024创新实验室-主任：卓大（原首页）
  * @Copyright  1024创新实验室 （ https://1024lab.net ），Since 2012
-->
<template>
  <MarketingDashboard />
</template>
<script setup>
  import MarketingDashboard from './dashboard/marketing-dashboard.vue';
</script>
<style lang="less" scoped>
  @import './index.less';
</style>
