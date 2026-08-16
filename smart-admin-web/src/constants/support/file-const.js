/*
 * 文件类型
 *
 * @Author:    1024创新实验室-主任：卓大
 * @Date:      2022-09-03 22:09:10
 * @Wechat:    zhuda1024
 * @Email:     lab1024@163.com
 * @Copyright  1024创新实验室 （ https://1024lab.net ），Since 2012
 */
// 文件上传类型
export const FILE_FOLDER_TYPE_ENUM = {
  COMMON: {
    value: 1,
    desc: '通用',
  },
  NOTICE: {
    value: 2,
    desc: '公告',
  },
  HELP_DOC: {
    value: 3,
    desc: '帮助中心',
  },
  FEEDBACK: {
    value: 4,
    desc: '意见反馈',
  },
};
/**
 * 分类编码。**新增分类一律用编码引用，不要用 ID** —— ID 各环境数据库各自生成，
 * 上面那个 FILE_FOLDER_TYPE_ENUM 之所以能用数字，是因为迁移脚本把四个内置分类的 ID
 * 对齐成了原 folderType 的值，这是历史特例不是惯例。
 */
export const FILE_CATEGORY_CODE = {
  COMMON: 'COMMON',
  // 运营主动上传的活动素材（主视觉、背景、分享图）
  ACTIVITY: 'ACTIVITY',
  // 富文本编辑器里随手插的正文配图。单独一个分类纯粹是为了素材库别乱：
  // 运营挑主视觉时不想在一堆正文截图里翻
  CONTENT: 'CONTENT',
};

export default {
  FILE_FOLDER_TYPE_ENUM,
  FILE_CATEGORY_CODE,
};
