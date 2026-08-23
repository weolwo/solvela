# t_task_template.rule_script 删列前的内容存档

> 导出时间：2026-08-23，删除 `t_task_template.rule_script` 列之前。
> 这 5 条内容**全部是注释，没有任何可执行逻辑**，且从未被任何 Java 代码读取执行过。
> 存档只为不静默丢失其中关于模板形态的说明。

## FRWAYF2X6N — 每日签到

```
// 事件: DAILY_SIGN；向导 rule_config 中的参数（如 targetDays、allowRepair）作为变量注入
if (event.type != 'DAILY_SIGN') return false;
// 连续性判定: 昨日未签且未补签则重置进度
if (!ctx.signedYesterday && !ctx.repaired) { record.currentMetric = 0; }
record.currentMetric = record.currentMetric + 1;
return record.currentMetric >= targetDays;
```

## T0ICGE0J6C — 每日签到

```
// 事件: DAILY_SIGN；向导 rule_config 中的参数（如 targetDays、allowRepair）作为变量注入
if (event.type != 'DAILY_SIGN') return false;
// 连续性判定: 昨日未签且未补签则重置进度
if (!ctx.signedYesterday && !ctx.repaired) { record.currentMetric = 0; }
record.currentMetric = record.currentMetric + 1;
return record.currentMetric >= targetDays;
```

## TP0COUNT01 — P0-累计签到

```
// P0 阶段进度由 CountTaskStrategy 计算，本脚本不参与运行态（rule_script 已降级为兜底通道）
```

## TP0LADDER1 — P0-阶梯签到

```
// 同上，阶梯由 t_task_prize_mapping 的 stage_level 表达
```

## TP0STREAK1 — P0-连续签到

```
// STREAK 由 StreakTaskStrategy 计算：断档归零再+1，容忍度由 tolerance 控制
```

