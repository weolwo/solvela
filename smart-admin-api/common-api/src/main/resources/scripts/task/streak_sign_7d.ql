/**
 * @name  连续签到满 7 天
 * @scene TASK_RULE
 * @desc  连续签到进度达到 7 天即判定达标。
 *        注意：P0 阶段这个判据由 StreakTaskStrategy 直接算，本脚本是「策略表达不了时怎么写」的样例。
 */
return currentMetric >= 7;
