/*
 * vite配置
 *
 * @Author:    1024创新实验室-主任：卓大
 * @Date:      2022-05-02 23:44:56
 * @Wechat:    zhuda1024
 * @Email:     lab1024@163.com
 * @Copyright  1024创新实验室 （ https://1024lab.net ），Since 2012
 */
import { resolve } from 'path';
import vue from '@vitejs/plugin-vue';
import { loadEnv } from 'vite';
import tailwindcss from '@tailwindcss/vite'; // [!新增] 1. 引入 tailwindcss v4 插件
import customVariables from './src/theme/custom-variables.js'; // ✅ 修改1：补充了文件后缀 .js

const pathResolve = (dir) => {
  return resolve(import.meta.dirname, '.', dir); // ✅ 修改2：将 __dirname 替换为 import.meta.dirname
};

export default ({ mode }) => {
  const env = loadEnv(mode, process.cwd());
  return {
    base: process.env.NODE_ENV === 'production' ? '/' : '/',
    root: process.cwd(),
    resolve: {
      alias: [
        // 国际化替换
        {
          find: 'vue-i18n',
          replacement: 'vue-i18n/dist/vue-i18n.cjs.js',
        },
        // 绝对路径重命名：/@/xxxx => src/xxxx
        {
          find: /\/@\//,
          replacement: pathResolve('src') + '/',
        },
        {
          find: /^~/,
          replacement: '',
        },
      ],
    },
    server: {
      host: '0.0.0.0',
      port: 8081,
      //
      // 🔴 这里刻意没有 proxy，不要再"顺手补上"。
      //
      // 前端是通过 axios 的 baseURL 直连后端的（src/lib/axios.js:24 读 VITE_APP_API_URL，
      // dev 环境即 http://127.0.0.1:1024），跨域由后端的 CORS 放行 —— 整条链路不经过 Vite 代理。
      //
      // 历史上这里有过一段写在 server.server.proxy 下的配置，因为嵌套层级不对
      // Vite 根本不识别，等于没配 —— 而"没配"恰好是对的，所以一直相安无事。
      // 2026-08-01 有人把它"修正"到正确层级后，dev server 立刻打不开了：
      // 代理模式是 '/'，它匹配的是**所有请求**，包括首页 HTML、/@vite/client、
      // 每一个源码模块请求，全被转发到 1024 端口，后端返回
      //     NoResourceFoundException: No static resource .
      // 表现就是"页面白屏 / 登录不了"，而根因跟登录、跟依赖升级都毫无关系。
      //
      // 真要加代理，必须给一个具体前缀（如 '/api'）并同步改 axios 的 baseURL，
      // 绝不能用 '/' 这种全匹配模式。
      //
    },
    // [!修改] 2. 将 tailwindcss() 注册到 plugins 数组中
    plugins: [vue(), tailwindcss()],
    optimizeDeps: {
      include: ['ant-design-vue/es/locale/zh_CN', 'dayjs/locale/zh-cn', 'ant-design-vue/es/locale/en_US'],
      exclude: ['vue-demi'],
    },
    build: {
      // 清除console和debugger
      terserOptions: {
        compress: {
          drop_console: true,
          drop_debugger: true,
        },
      },
      rollupOptions: {
        output: {
          //配置这个是让不同类型文件放在不同文件夹，不会显得太乱
          chunkFileNames: 'js/[name]-[hash].js',
          entryFileNames: 'js/[name]-[hash].js',
          assetFileNames: '[ext]/[name]-[hash].[ext]',
          manualChunks(id) {
            //静态资源分拆打包
            if (id.includes('node_modules')) {
              return id.toString().split('node_modules/')[1].split('/')[0].toString();
            }
          },
        },
      },
      target: 'esnext',
      outDir: 'dist', // 指定输出路径
      assetsDir: 'assets', // 指定生成静态文件目录
      assetsInlineLimit: '4096', // 小于此阈值的导入或引用资源将内联为 base64 编码
      chunkSizeWarningLimit: 500, // chunk 大小警告的限制
      minify: 'terser', // 混淆器，terser构建后文件体积更小
      emptyOutDir: true, //打包前先清空原有打包文件
    },
    css: {
      preprocessorOptions: {
        less: {
          modifyVars: customVariables,
          javascriptEnabled: true,
        },
      },
    },
    define: {
      __INTLIFY_PROD_DEVTOOLS__: false,
    },
  };
};