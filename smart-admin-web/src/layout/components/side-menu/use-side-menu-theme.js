/*
 * 侧边菜单明暗主题
 *
 * 两个地方要用同一个判断：
 *   1、side-menu/index.vue 给根节点加 .is-dark；
 *   2、side-layout.vue 给 a-layout-sider 本身上底色（菜单不够一屏高时露出的那块）。
 * 放一处，避免两边取值不一致导致底色和卡片对不上。
 *
 * 触发深色的条件有两个：菜单主题选了 dark，或者全站开了夜间模式。
 */
import { computed } from 'vue';
import { useAppConfigStore } from '/@/store/modules/system/app-config';

// iOS 分组列表背景色，需与 index.vue / menu-tree.vue 里的 bg-[...] 保持一致
export const SIDE_MENU_BG_LIGHT = '#F2F2F7';
export const SIDE_MENU_BG_DARK = '#000000';

export function useSideMenuTheme() {
  const darkFlag = computed(() => {
    const state = useAppConfigStore().$state;
    return state.sideMenuTheme === 'dark' || state.darkModeFlag;
  });

  const sideMenuBg = computed(() => (darkFlag.value ? SIDE_MENU_BG_DARK : SIDE_MENU_BG_LIGHT));

  return { darkFlag, sideMenuBg };
}
