/**
 * @name  VIP 奖池准入
 * @scene POOL_ENTRY
 * @desc  样例脚本：只放行 VIP_POOL 这个池子。
 *        真实判据（会员等级、历史消费额）要等 member 域的 @ScriptFunction 铺开后才写得出来，
 *        那时这里会变成 member_getLevel(memberId) >= 3 这种形态。
 */
return poolCode == "VIP_POOL" && memberId > 0;
