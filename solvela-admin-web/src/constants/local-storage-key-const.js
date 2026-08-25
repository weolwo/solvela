/*
 *  key  常量
 *
 * @Author:    1024创新实验室-主任：卓大
 * @Date:      2022-09-06 19:58:50
 * @Wechat:    zhuda1024
 * @Email:     lab1024@163.com
 * @Copyright  1024创新实验室 （ https://1024lab.net ），Since 2012
 */

/**
 * key前缀
 */
const KEY_PREFIX = 'solvela_admin_';
/**
 * localStorageKey集合
 */
export default {
  // 用户token
  USER_TOKEN: `${KEY_PREFIX}user_token`,
  // 用户权限点
  USER_POINTS: `${KEY_PREFIX}user_points`,
  // 用户的tag列表
  USER_TAG_NAV: `${KEY_PREFIX}user_tag_nav`,
  // app config 配置信息
  APP_CONFIG: `${KEY_PREFIX}app_config`,
  // 首页快捷入口
  HOME_QUICK_ENTRY: `${KEY_PREFIX}home_quick_entry`,
  // 通知信息已读
  NOTICE_READ: `${KEY_PREFIX}notice_read`,
  // 待办
  TO_BE_DONE: `${KEY_PREFIX}to_be_done`,
  // 任务模板设计器 本地草稿（防浏览器崩溃/误关丢失未保存的 schema 与脚本）
  TASK_TEMPLATE_DRAFT: `${KEY_PREFIX}task_template_draft`,
  // 任务配置向导 本地草稿（含填写进度，防中途丢失）
  TASK_WIZARD_DRAFT: `${KEY_PREFIX}task_wizard_draft`,
};
