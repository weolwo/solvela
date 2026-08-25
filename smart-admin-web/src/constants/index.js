/*
 * 所有常量入口
 *
 * @Author:    1024创新实验室-主任：卓大
 * @Date:      2022-09-06 19:58:28
 * @Wechat:    zhuda1024
 * @Email:     lab1024@163.com
 * @Copyright  1024创新实验室 （ https://1024lab.net ），Since 2012
 */
import menu from './system/menu-const';
import { LOGIN_DEVICE_ENUM } from './system/login-device-const';
import { FLAG_NUMBER_ENUM, GENDER_ENUM, USER_TYPE_ENUM } from './common-const';
import { LAYOUT_ENUM } from './layout-const';
import file from './support/file-const';
import loginLog from './support/login-log-const';
import codeGeneratorConst from './support/code-generator-const';
import jobConst from './support/job-const';
import dictConst from './support/dict-const';
// 只按需具名引入彩票的两个「状态枚举」，不要 ...lotteryConst 整包展开 ——
// lottery-const 里还有 MIN_NUMBER_LENGTH（数字）、prizeIcon（函数）这类非枚举，
// 整包塞进枚举表只会污染 SmartEnum* 的查找空间
import { LOTTERY_STATUS_ENUM, WIN_STATUS_ENUM } from './business/lottery/lottery-const';

export default {
  FLAG_NUMBER_ENUM,
  LOGIN_DEVICE_ENUM,
  GENDER_ENUM,
  USER_TYPE_ENUM,
  LAYOUT_ENUM,
  ...loginLog,
  ...menu,
  ...file,
  ...codeGeneratorConst,
  ...jobConst,
  ...dictConst,
  LOTTERY_STATUS_ENUM,
  WIN_STATUS_ENUM,
};
