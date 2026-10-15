package solvela.member.risk.feature;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 一个会员的登录画像：他<b>平时</b>在哪些网段、哪些国家、上一次在哪、来过几次。
 *
 * <h3>它只用来回答一个问题</h3>
 * 「这一次的环境像不像他平时那些环境」。所以里面没有任何「当时判成了什么」的历史结论 ——
 * 那种数据会让同一次登录在不同时间得出不同答案。
 *
 * @param recentPrefixes 近 90 天成功登录过的网段（含注册 IP 的网段），最多留 10 个。严格熟悉用它
 * @param recentPlaces   同一批 IP 的「国家|省|市|运营商」。宽松熟悉用它
 * @param countries      近 90 天出现过的国家
 * @param lastSuccess    上一次成功登录（<b>任何设备上的</b>）；没有则为 null
 * @param successCount   近 90 天的成功登录次数。冷启动判断用它
 * @Date 2026-10-15
 */
public record LoginProfile(Set<String> recentPrefixes, Set<String> recentPlaces, Set<String> countries,
                           LastSuccess lastSuccess, int successCount) {

    /** 什么都没有的画像。新号、或者登录日志查不到时就是它 */
    public static final LoginProfile EMPTY = new LoginProfile(Set.of(), Set.of(), Set.of(), null, 0);

    /**
     * 上一次成功登录的地点与时间。
     *
     * <p>🔴 存国家与省份，不存城市：不可能旅行只按国家和省判
     * （城市一级的噪声太大，手机切一次基站就能跳一个城市）。
     */
    public record LastSuccess(String country, String province, LocalDateTime time) {
    }

    /**
     * 这一次的网段在不在常用列表里。
     *
     * <p>🔴 画像为空时返回 false ——「没有画像」不等于「熟悉」。新号在新网络上登录
     * 本来就该多验一道，那是矩阵的本意。
     */
    public boolean knownPrefix(String prefix) {
        return prefix != null && recentPrefixes.contains(prefix);
    }

    /** 同城市 + 同运营商出现过（后置的宽松口径） */
    public boolean knownPlace(String placeKey) {
        return placeKey != null && recentPlaces.contains(placeKey);
    }

    public boolean knownCountry(String country) {
        return country != null && countries.contains(country);
    }
}
