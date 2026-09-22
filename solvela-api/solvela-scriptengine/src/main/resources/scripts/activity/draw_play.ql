/**
 * @name  活动玩法编排-抽奖(样例)
 * @scene ACTIVITY_PLAY
 * @desc  样例脚本：按【会员等级】做抽奖额度与奖池的差异化。
 *        真实活动请复制一份改成自己的判据 —— 奖池编码、次数上限、人群条件都在这里定，
 *        改脚本不用发版，这正是把这段决策放进脚本的原因。
 *
 *        ⚠️ 2026-09-22 改：上一版是从 params['tier'] 取「档位」决定奖池的。
 *        换掉的理由是那个值【由前端传】—— 用户改一个请求参数就能挑到更好的池子，
 *        而挑池子正是这段脚本存在的理由。等级由服务端从成长值算，伪造不了。
 *
 *        必须守的三条规矩：
 *        1) draw_executeMultiDrawByScript 有副作用，一次执行只准调一次，且必须是最后一步；
 *           两个 if 分支各写一次而条件同时成立时，引擎会直接拦下并抛出。
 *        2) 不要读 params 里的 poolCode —— 那等于让客户端自己挑池子抽。
 *        3) 🔴 不想让他抽时【不要 return null】。null 会被场景契约判成违约并抛出，
 *           用户看到的是一句点名脚本的开发者向报错。用 draw_rejectQuotaExceeded()
 *           或 draw_rejectNotEligible()，它们会翻成给用户看的话。
 */

// 非抽奖类活动挂错了脚本：返回 null 会被场景契约当成「脚本没有返回值」而报错，
// 报错信息里会点名是哪个脚本 —— 这种情况是【配置事故】，报错是对的
if (activityType != 'DRAW') {
    return null;
}

/*
 * ① 参与门槛：这个样例只对银卡及以上开放。
 *
 * member_gradeAtLeast 取的是会员【当前所在】的等级，不是「成长值够到哪一档」——
 * 保级缓冲期里他挂着高等级但成长值够不着，那时他仍然是那一档的会员，
 * 权益不该被收走（缓冲期本来就是挽留）。
 */
if (!member_gradeAtLeast(1)) {
    return draw_rejectNotEligible();
}

/*
 * ② 额度：等级越高，每个周期抽得越多。
 *
 * 周期由抽奖配置的 reset_period 决定（DAY/WEEK/MONTH/ACTIVITY），
 * draw_countDrawn() 按那个周期数。所以这里只写「几次」，不写「每天」——
 * 把周期写死在脚本里，运营改配置时这段就悄悄不对了。
 */
quota = 3;
if (member_gradeAtLeast(4)) {
    quota = 12;
} else if (member_gradeAtLeast(3)) {
    quota = 8;
} else if (member_gradeAtLeast(2)) {
    quota = 5;
}

if (draw_countDrawn() >= quota) {
    return draw_rejectQuotaExceeded();
}

/*
 * ③ 奖池：高等级走专享池。
 *
 * ⚠️ 池子必须在这个活动下真实存在且已开启，否则会被拒成 POOL_NOT_FOUND ——
 * 对用户是「活动暂时无法参与」，而运营会以为是等级判错了。改等级门槛时，
 * 顺手确认一下对应的池子建了没有。
 */
pool = member_gradeAtLeast(3) ? 'DRAW_POOL_VIP' : 'DRAW_POOL_NORMAL';

return draw_executeMultiDrawByScript(pool, 1);
