# 业务脚本目录

**这里是脚本内容的唯一权威。**`t_script` 表只是它的只读镜像 —— 应用启动时由
`ScriptFileLoader` 把这里的文件同步进去，改数据库不生效、下次启动就被覆盖。

改脚本 = 改文件 + code review + 发版。后台的脚本页只能「查看 + 在线试跑」，不能编辑。

## 为什么是文件不是数据库

| | 文件（当前方案） | 数据库可编辑 |
|---|---|---|
| 语法错误何时暴露 | **应用启动就失败**，坏脚本进不了生产 | 运营点保存那一刻，或者更晚 |
| 能不能 review | 能，走 PR | 不能 |
| 能不能回滚 | `git revert` | 得自己做版本表 |
| 改了谁知道 | git blame | 猜 |
| 运营能自助改吗 | **不能**，要发版 | 能 |

最后一行是代价，其余四行是收益。这个取舍已经确认，不要在没有新信息的情况下推翻。

## 目录规则

```
scripts/{域}/{脚本名}.ql
```

- **文件夹必须等于场景所属域的 namespace**（`ScriptScene.getDomain().getNamespace()`）。
  把 `TASK_RULE` 的脚本放进 `draw/` 会在启动时直接报错 —— 这条规则的存在就是为了让目录树不撒谎。
- **`script_code` 由路径推导**：`scripts/task/streak_sign_7d.ql` → `task/streak_sign_7d`。
  不要在文件头里再写一遍 code，能推导出来的东西不留给人写。
- 文件名用小写下划线，别用中文。

## 文件头格式

每个脚本必须以一段块注释开头，三个标签缺一不可：

```javascript
/**
 * @name  连续签到满 7 天
 * @scene TASK_RULE
 * @desc  连续签到进度达到 7 天即判定达标。
 *        可以写多行，缩进对齐即可。
 */
return currentMetric >= 7;
```

| 标签 | 必填 | 说明 |
|---|---|---|
| `@name` | 是 | 中文名，后台列表显示用 |
| `@scene` | 是 | 场景枚举名，见 `ScriptScene`。**它决定了这个脚本能拿到哪些变量、必须返回什么** |
| `@desc` | 是 | 用途说明，支持多行 |

域不用写 —— 由 `@scene` 推导（`ScriptScene.getDomain()`）。

## 能用哪些变量和函数

- **变量**：由 `@scene` 决定。查 `ScriptScene` 枚举，或调 `GET /script/engine/scene/view`。
  用了场景没声明的变量，取到的是 `null`，不会报错 —— 这是 QL 的语义，注意。
- **函数**：所有 `@ScriptFunction` 暴露的方法，一律带域前缀（`tool_` / `member_` / `mall_` …）。
  查 `GET /script/engine/view` 或后台的「脚本方法文档」页。

## 🔴 写脚本时最容易踩的两个坑

**1. 分支没覆盖全 = 静默返回 null**

QLExpress 里最后一个表达式的值就是返回值，所以「漏写 return」通常没事。真正危险的是这个：

```javascript
if (memberId < 0) {
    return true;
}
// memberId >= 0 时走到这里，整段脚本没有返回值 → null
```

场景契约会在执行后拦下它并报错。但你自己写的时候就该避免 —— **每条分支都要有返回值**。

**2. 多行表达式的操作符必须留在上一行末尾**

```javascript
return baseScore +          // ✅ 加号在上一行末尾
       bonusScore;

return baseScore            // ❌ 会被当成两条语句
       + bonusScore;
```

## 新增一个脚本

1. 按上面的规则建文件
2. 启动应用 —— 语法错、场景不存在、目录放错，都会**直接启动失败**并指出是哪个文件
3. 在后台「脚本管理」页确认它已入库
4. 把它挂到业务对象上（`t_script_ref`），或用 `ScriptRuntime` 在代码里按挂载点调用

## 当前这三个脚本是什么

**格式样例，没有挂到任何业务对象上。**它们的作用是：证明加载链路是通的、给新脚本当模板。
`t_script_ref` 里查不到它们，所以随时可以删。

真正有业务含义的脚本要等 `member` / `risk` / `mall` 等域的 `@ScriptFunction` 铺开之后才写得出来 ——
现在只有 `tool_` 域的 5 个纯函数，表达不了任何真实的营销判据。
