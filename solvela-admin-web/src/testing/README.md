# 管理端测试

```bash
npm run test          # 跑一遍
npm run test:watch    # 改一处跑一处
```

## 为什么 2026-09-24 才有

在此之前这个应用**一个测试都没有**，也没有 lint 与类型检查 —— `package.json` 里只有
`dev` 和三个 `build`。所有验证靠人肉开浏览器点。

代价是真实发生过的：

| 出过的事 | 表现 | 谁本该发现 |
|---|---|---|
| `member-grade-config-list.vue` 通篇写 `record.currentGrade`，接口给的是 `gradeCode` | 「等级 N」标签一直是空的；等级 0 的停用开关**从来没被禁用过** | `member-grade-config-list.spec.js` |
| 收藏统计的「积分」列直接发商品基准价 | 某商品显示成真实价格的 **100 倍**，而那一列正是运营判断「定价偏高」的依据 | `mall-favorite-list.spec.js` |
| C 端预览自己算价，和真实 C 端漂了 | 预览对、C 端错，两边对不上而没有任何机制会发现 | `c-side-preview.spec.js` |
| `lottery-record-list.vue` 的 import 指向不存在的模块 | 只有点进那个菜单才炸 | `views-import-smoke.spec.js` |

四种都**不报错、不抛异常**，只是少显示了一点东西或多显示了一个错的数。

## 怎么写

**断言渲染出来的字，不要断言内部状态。** 上面第一条里 `record.currentGrade` 就是
`undefined` —— 一个合法的值，内部状态怎么看都对，只有渲染结果能把它抖出来。

**假数据的字段名照着后端的 record 写，不要照着页面写。** 照页面写的话，
页面写错了假数据也跟着错，用例永远绿。

### 三个已知的坑

**1. `a-drawer` / `a-modal` 的内容是 teleport 到 body 的**

`wrapper.html()` 里只有一对 `<!--teleport start/end-->` 注释。要 `attachTo: document.body`
然后断言 `document.body.innerHTML`。

别用 `stubs: { teleport: true }` 绕 —— 那会把整段内容换成一个占位符，
等于把要测的东西 stub 掉了，而失败长得像「组件坏了」。

**2. 桩函数要返回 rejected promise 时，不要用 `vi.fn`**

`vi.fn` 为了填 `mock.settledResults`，会在被测代码拿到的那个 promise 上再挂一条
`.then` 链。返回 rejected promise 时那条**派生链没有人 catch**，vitest 会报一条
"Unknown Error" 把用例判红 —— 而组件的 `try/catch` 明明接住了。

用普通函数做桩，调用记录自己存一份（见 `c-side-preview.spec.js` 里的
`previewImpl` / `previewCalls`）。

**3. 拒绝值用归一后的对象，不是 `new Error`**

这个应用的 axios 拦截器把 4xx 归一成 `{ status, code, message, traceId }` 再 reject
（见 `lib/axios.js`），组件拿到的从来不是一个 `Error` 实例。
用 `Error` 造的用例是在一个真实不存在的形状上跑。

## 为什么没有类型检查

这是个纯 JS 项目（只有 `jsconfig.json`，没有 `tsconfig.json`）。开 `checkJs` 会在存量代码上
炸出几千条，而「红着也得发版」的检查等于没有检查。

要类型的话正确顺序是**先把新文件写成 `.ts`**，不是今天把开关一拉。
