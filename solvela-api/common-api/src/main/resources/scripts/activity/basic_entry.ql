/**
 * @name  活动准入-基础放行
 * @scene ACTIVITY_RULE
 * @desc  样例脚本：演示场景变量的引用方式。
 *        真实的准入判据（黑名单、等级门槛、地域限制）依赖 member / risk 两个域的函数，
 *        那两个域目前还没有函数，所以这里只做最基础的校验。
 */
return memberId > 0 && !tool_isBlank(activityCode);
