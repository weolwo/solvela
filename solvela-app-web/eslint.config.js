import js from '@eslint/js'
import prettier from 'eslint-config-prettier'
import vue from 'eslint-plugin-vue'
import globals from 'globals'
import tseslint from 'typescript-eslint'
import vueParser from 'vue-eslint-parser'

export default tseslint.config(
  { ignores: ['dist/**', 'coverage/**', 'src/components.d.ts'] },

  js.configs.recommended,
  ...tseslint.configs.recommendedTypeChecked,
  ...vue.configs['flat/recommended'],

  {
    languageOptions: {
      globals: { ...globals.browser },
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
        extraFileExtensions: ['.vue'],
      },
    },
    rules: {
      // ---- 这三条是本项目的地雷，不是风格偏好，别关 ----

      // 后端 BigDecimal 走 ToStringSerializer，金额永远是字符串；
      // ID 走 LongJsonSerializer，超过 2^53-1 也是字符串。
      // 对这类值做隐式转换（+、==、拼接后再算）是最典型的翻车方式。
      '@typescript-eslint/restrict-plus-operands': 'error',
      '@typescript-eslint/no-unsafe-argument': 'error',
      eqeqeq: ['error', 'always'],

      // ---- 其余 ----
      '@typescript-eslint/consistent-type-imports': [
        'error',
        { prefer: 'type-imports', fixStyle: 'inline-type-imports' },
      ],
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
      'vue/multi-word-component-names': 'off',

      /*
       * 与 tsconfig 的 exactOptionalPropertyTypes 直接冲突，必须关。
       *
       * 这条规则要求每个可选 prop 都给默认值，而在 <script setup> + TS 下
       * 可选 prop 的默认值只能写成 `undefined` —— 那正是
       * exactOptionalPropertyTypes 判为类型错误的写法（在那个开关下，
       * 「没传这个 prop」和「传了 undefined」是两件事）。
       * 两者只能留一个，留类型检查：它拦得住的东西更多。
       */
      'vue/require-default-prop': 'off',
      'no-console': ['warn', { allow: ['warn', 'error'] }],
    },
  },

  {
    // .vue 的 <script> 块要由 vue-eslint-parser 转交给 @typescript-eslint/parser，
    // 否则所有需要类型信息的规则都会在 .vue 上直接报错退出。
    files: ['**/*.vue'],
    languageOptions: {
      parser: vueParser,
      parserOptions: {
        parser: tseslint.parser,
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
        extraFileExtensions: ['.vue'],
      },
    },
  },

  {
    files: ['src/**/__tests__/**/*.ts'],
    languageOptions: { globals: { ...globals.node } },
  },

  {
    files: ['*.config.ts', '*.config.js'],
    languageOptions: { globals: { ...globals.node } },
    ...tseslint.configs.disableTypeChecked,
  },

  prettier,
)
