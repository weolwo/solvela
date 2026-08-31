package scripts.draw

if (activityType != "DRAW") {
    return null;
}
/**
 默认奖池
 **/
poolCode = "DB24OPCQ0K";
return draw_executeDrawByScript(poolCode);