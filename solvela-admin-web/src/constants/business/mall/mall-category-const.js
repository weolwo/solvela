/**
 * 商城-商品分类 枚举与常量
 *
 * 与后端 sa.mall.constant.MallConst 同源，改动时两边必须一起改。
 *
 * @Author:    weolwo
 * @Date:      2026-08-22 19:28:16
 * @Copyright  weolwo
 */

export const CATEGORY_STATUS_ENUM = {
  DISABLED: { value: 0, desc: '停用', color: 'default' },
  ENABLED: { value: 1, desc: '启用', color: 'green' },
};

export const CATEGORY_STATUS_OPTIONS = Object.values(CATEGORY_STATUS_ENUM).map((item) => ({
  value: item.value,
  label: item.desc,
}));

/**
 * 顶级分类的 parentId。
 *
 * DDL 里 parent_id 是 `NOT NULL DEFAULT 0`，不是可空列 —— 顶级用 0 表示而不是 null，
 * 表单里的「顶级分类」选项也用这个值，别在前端翻译成 null 再传。
 */
export const ROOT_PARENT_ID = 0;

/**
 * 业务上限死两级。不卡的话运营能建出五级菜单，而 C 端宫格导航只渲染两层，
 * 第三层往下直接消失 —— 服务端也会拒绝，这里只是让界面提前说清楚。
 */
export const MAX_CATEGORY_LEVEL = 2;

/**
 * 批量新建的单次上限（父 + 子一起算），与后端 MallConst.MAX_CATEGORY_BATCH 同源。
 *
 * 不是技术限制，是表单本身的限制：一屏放不下几十行，运营录到一半滚屏找位置
 * 比分两次提交更容易出错。
 */
export const MAX_CATEGORY_BATCH = 10;

export default {
  CATEGORY_STATUS_ENUM,
  ROOT_PARENT_ID,
  MAX_CATEGORY_LEVEL,
  MAX_CATEGORY_BATCH,
};
