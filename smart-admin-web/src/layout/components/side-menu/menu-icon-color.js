/*
 * 侧边菜单图标磁贴配色
 *
 * 菜单图标存在数据库（t_menu.icon）里，只有图标名没有颜色。
 * 这里按一级菜单的 menuId 做哈希取色，保证：
 *   1、同一个模块每次刷新颜色都一样（不随菜单排序变化）；
 *   2、子菜单继承一级菜单的颜色，整棵子树是一个色系。
 *
 * 取的是 iOS 系统色，light / dark 各一套（深色模式下 Apple 会把同一个色相调亮一档），
 * 磁贴始终是实色底 + 纯白图标，这是「原生 App 质感」的关键，不做半透明浅底。
 */

export const MENU_ICON_COLORS = [
  { light: '#007AFF', dark: '#0A84FF' }, // blue
  { light: '#34C759', dark: '#30D158' }, // green
  { light: '#FF9500', dark: '#FF9F0A' }, // orange
  { light: '#FF3B30', dark: '#FF453A' }, // red
  { light: '#AF52DE', dark: '#BF5AF2' }, // purple
  { light: '#30B0C7', dark: '#40C8E0' }, // teal
  { light: '#5856D6', dark: '#5E5CE6' }, // indigo
  { light: '#FF2D55', dark: '#FF375F' }, // pink
  { light: '#A2845E', dark: '#AC8E68' }, // brown
  { light: '#8E8E93', dark: '#8E8E93' }, // gray
];

/**
 * 字符串哈希：让相邻的 menuId 也能散开到不同颜色，避免整屏同色
 */
function hashCode(str) {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = (hash << 5) - hash + str.charCodeAt(i);
    hash |= 0;
  }
  return Math.abs(hash);
}

/**
 * 根据菜单id获取磁贴配色
 * @param menuId 一级菜单的 menuId
 */
export function getMenuIconColor(menuId) {
  if (menuId === undefined || menuId === null) {
    return MENU_ICON_COLORS[0];
  }
  return MENU_ICON_COLORS[hashCode(String(menuId)) % MENU_ICON_COLORS.length];
}
