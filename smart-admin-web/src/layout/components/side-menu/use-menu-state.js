/*
 * 菜单状态（四种布局共用）
 *
 * MenuNode 通过 inject('smartSideMenu') 拿到展开/选中/跳转四个方法，
 * 这里统一提供，避免 side / top / side-expand / top-expand 各写一份。
 *
 * 各布局的差异用参数表达：
 *   accordionKeys  哪一层算「根级可展开项」——单开模式下这一层互斥。
 *                  side 是一级；top 是二级（一级在顶栏上）。
 *   searchingFlag  搜索态：无条件全展开，且手动折叠不生效。
 */
import _ from 'lodash';
import { computed, provide, ref } from 'vue';
import { useRoute } from 'vue-router';
import { router } from '/@/router/index';
import { useAppConfigStore } from '/@/store/modules/system/app-config';
import { useUserStore } from '/@/store/modules/system/user';

export function useSmartMenuState(options = {}) {
  const { accordionKeys = null, searchingFlag = null } = options;

  const menuSingleExpandFlag = computed(() => useAppConfigStore().$state.menuSingleExpandFlag);
  const currentRoute = useRoute();

  const selectedKey = ref(null);
  const openKeys = ref([]);
  // 当前页所属的一级菜单，顶栏/第一列用它做高亮
  const activeTopId = ref(null);

  function isOpen(menuId) {
    if (searchingFlag && searchingFlag.value) {
      return true;
    }
    return openKeys.value.includes(menuId);
  }

  function isSelected(menuId) {
    return selectedKey.value === menuId;
  }

  function onToggle(menuId) {
    // 搜索态下展开由 isOpen 接管，手动折叠不落库
    if (searchingFlag && searchingFlag.value) {
      return;
    }
    if (openKeys.value.includes(menuId)) {
      openKeys.value = openKeys.value.filter((e) => e !== menuId);
      return;
    }
    const keys = accordionKeys ? accordionKeys.value || [] : [];
    if (menuSingleExpandFlag.value && keys.includes(menuId)) {
      openKeys.value = [menuId];
    } else {
      openKeys.value = [...openKeys.value, menuId];
    }
  }

  // 页面跳转
  function onNavigate(menu) {
    useUserStore().deleteKeepAliveIncludes(menu.menuId.toString());
    router.push({ path: menu.path });
  }

  provide('smartSideMenu', { isOpen, isSelected, onToggle, onNavigate });

  /**
   * SmartAdmin中 router的name 就是 后端存储menu的id，
   * 所以可以直接根据路由算出选中项和需要展开的祖先链。
   *
   * @param mode 'auto'   按「设置-菜单展开」决定替换还是合并（side/侧边用）
   *             'merge'  始终合并，不收起用户已展开的（top/顶栏用）
   *             'none'   只更新选中，不动 openKeys（展开型布局自己控制）
   */
  function syncFromRoute(mode = 'auto') {
    selectedKey.value = _.toNumber(currentRoute.name);

    const menuParentIdListMap = useUserStore().getMenuParentIdListMap;
    const parentList = menuParentIdListMap.get(currentRoute.name) || [];
    const needOpenKeys = _.map(parentList, 'name').map(Number);

    // 没有父级说明当前页本身就是一级菜单
    activeTopId.value = needOpenKeys.length ? needOpenKeys[0] : _.toNumber(currentRoute.name);

    if (mode === 'none') {
      return needOpenKeys;
    }
    if (mode === 'merge' || !menuSingleExpandFlag.value) {
      // 使用lodash的union函数，进行 去重合并两个数组
      openKeys.value = _.union(openKeys.value, needOpenKeys);
    } else {
      openKeys.value = [...needOpenKeys];
    }
    return needOpenKeys;
  }

  return { currentRoute, selectedKey, openKeys, activeTopId, isOpen, isSelected, onToggle, onNavigate, syncFromRoute };
}
