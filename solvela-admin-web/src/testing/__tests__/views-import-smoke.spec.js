import { describe, expect, it } from 'vitest';

/**
 * 每一个业务页面都要能被<b>导入</b>。
 *
 * <h3>🔴 它守的是「import 写坏了」这一类</h3>
 * 这个仓库出过：{@code lottery-record-list.vue} 的 import 指向一个不存在的模块，
 * 而它只在<b>运行到那个菜单</b>时才炸。管理端有上百个页面，
 * 谁也不会每次发版前把每个菜单都点一遍 —— 于是它活到了下一次有人点进去。
 *
 * <p>这条用例把「点进去」提前到构建之前：只 import，不 mount。
 * 跑得比 `npm run build:test` 快，而且失败信息直接指向那个文件。
 *
 * <h3>⚠️ 它<b>不</b>验证页面能正常渲染</h3>
 * 能 import ≠ 能用。渲染要靠各自的 spec（见 member-grade / mall-favorite / c-side-preview）。
 * 这条只回答一个问题：<b>这个文件还在不在、它依赖的东西还在不在。</b>
 *
 * <p>⚠️ 用 `import.meta.glob` 而不是 fs + 动态 import()：
 * 后者要自己拼路径，在 Windows 上还要处理 `/D:/...` 那种形状，
 * 而且带变量的动态 import 过不了 Vite 的别名解析（`/@/` 会原样留着）。
 * glob 是构建期展开的，路径和别名都由 Vite 自己算。
 *
 * <p>⚠️ 不要在这里加「页面数量必须等于 N」的断言：新增页面是常态，
 * 那种断言只会让每个加页面的人顺手把它改掉，久而久之没人再看这个文件。
 */

/* eager: false —— 逐个 import，失败时能定位到是哪一个文件 */
const modules = import.meta.glob('../../views/**/*.vue');

const entries = Object.entries(modules)
  // __tests__ 里是测试自己，不是页面
  .filter(([path]) => !path.includes('/__tests__/'));

describe('业务页面导入冒烟', () => {
  it('🔴 扫到的页面数量不能为 0 —— 空扫通过比没有这条用例更糟', () => {
    expect(entries.length).toBeGreaterThan(20);
  });

  it.each(entries.map(([path, load]) => [path.replace('../../views/', ''), load]))(
    'views/%s 能被导入',
    async (_name, load) => {
      const mod = await load();
      expect(mod.default).toBeTruthy();
    },
  );
});
