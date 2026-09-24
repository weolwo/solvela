/**
 * 管理端测试的公共兜底。
 *
 * <h3>🔴 jsdom 没有实现 window.matchMedia，而 ant-design-vue 启动就要它</h3>
 * `a-row` 在 mounted 里订阅响应式断点（`responsiveObserve`），
 * 缺了它会直接抛 `window.matchMedia is not a function`。
 *
 * <p>⚠️ 抛出的位置是 Vue 的 post-flush 钩子里，表现是<b>整个组件树渲染不出来</b>，
 * 而报错信息指向 ant-design-vue 的内部文件 —— 看上去像是库的问题，
 * 其实只是环境缺了一个浏览器 API。管理端几乎每个页面都有 a-row，
 * 所以这一条放公共 setup，而不是每个 spec 自己补一遍。
 *
 * <p>C 端（app-web）那边是每个 spec 内联补的，因为只有带 Carousel 的那几个页面需要。
 */
if (typeof window !== 'undefined' && typeof window.matchMedia !== 'function') {
  window.matchMedia = (query) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  });
}

/*
 * ⚠️ jsdom 也没有 getComputedStyle 之外的布局能力，
 *    ant-design-vue 的 Table 在某些版本里会读 scrollWidth/offsetWidth 做列宽计算。
 *    它们在 jsdom 里恒为 0 —— 不抛错，只是列宽算成 0，不影响我们断言渲染出来的【文字】。
 *    所以这里不去假造布局：假造了反而会让用例在一个和真实浏览器都不一样的环境里绿。
 */
