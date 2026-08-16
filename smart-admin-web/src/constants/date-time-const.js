/*
 * 日期时间选择器的默认时间
 *
 * 解决的问题：带 show-time 的选择器，选完日期后时间部分默认取「当前时刻」，
 * 用户还得再点进时间面板改成 00:00:00 / 23:59:59，一个查询条件要点四五次。
 * 绝大多数场景其实只关心日期，时间应该自动取当天的起止。
 *
 * 用法：
 *   起止范围   <a-range-picker :show-time="RANGE_SHOW_TIME" />
 *   开始时间   <a-date-picker  :show-time="DAY_START_SHOW_TIME" />
 *   结束时间   <a-date-picker  :show-time="DAY_END_SHOW_TIME" />
 *
 * ⚠️ 只用于「起始 / 结束」语义的字段。
 *    像开奖时间、发布时间这类**时间点**字段不要套用 —— 把它默认成 0 点会改变业务含义。
 */
import dayjs from 'dayjs';

/** 当天起点 00:00:00 */
export const dayStart = () => dayjs().startOf('day');

/** 当天终点 23:59:59 */
export const dayEnd = () => dayjs().endOf('day');

/** a-range-picker 用：左侧补 00:00:00，右侧补 23:59:59 */
export const RANGE_SHOW_TIME = { defaultValue: [dayStart(), dayEnd()] };

/** a-date-picker 用：开始时间补 00:00:00 */
export const DAY_START_SHOW_TIME = { defaultValue: dayStart() };

/** a-date-picker 用：结束时间补 23:59:59 */
export const DAY_END_SHOW_TIME = { defaultValue: dayEnd() };
