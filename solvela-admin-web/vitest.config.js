import { resolve } from 'path';

import vue from '@vitejs/plugin-vue';
import { defineConfig } from 'vitest/config';

/**
 * 管理端的测试配置。
 *
 * <h3>🔴 为什么单独一个文件，不复用 vite.config.js</h3>
 * 那个文件导出的是 `({ mode }) => ({...})` 函数式配置，靠 `loadEnv(mode)` 读 .env，
 * 而 vitest 不会传 mode 进来。硬接的后果是构建和测试互相牵制 ——
 * 改一个配置要同时想着另一件事，而那正是「以后没人敢动」的开始。
 *
 * <h3>为什么管理端到 2026-09-24 才有测试</h3>
 * 在此之前这个应用<b>一个测试都没有</b>，也没有 lint 与类型检查：
 * package.json 里只有 dev 和三个 build。所有验证靠人肉开浏览器。
 *
 * 代价是真实发生过的：{@code member-grade-config-list.vue} 里通篇写的是
 * `record.currentGrade`，而接口返回的字段叫 `gradeCode` ——
 * 「等级 N」标签一直是空的、等级 0 的停用开关<b>从来没有被禁用过</b>，
 * 而这件事活了好几天没人发现。它不报错、不抛异常，只是少显示了一点东西。
 *
 * ⚠️ 这里刻意<b>不</b>接 vue-tsc：这是个纯 JS 项目（只有 jsconfig，没有 tsconfig），
 * 开 checkJs 会在存量代码上炸出几千条，而那种「红着也得发版」的检查等于没有检查。
 * 要类型的话，正确顺序是先把新文件写成 .ts，不是今天把开关一拉。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: [
      // 和 vite.config.js 同一条：/@/xxx => src/xxx。两边都要改，漏改的表现是测试里 import 失败
      { find: /\/@\//, replacement: resolve(import.meta.dirname, 'src') + '/' },
    ],
  },
  test: {
    environment: 'jsdom',
    // jsdom 缺的浏览器 API 在这里统一补，理由见该文件
    setupFiles: ['src/testing/setup.js'],
    globals: false,
    include: ['src/**/__tests__/**/*.spec.js'],
    // 依赖 ant-design-vue 的组件树很深，默认 5s 在冷启动那几条上会误报超时
    testTimeout: 15000,
  },
});
