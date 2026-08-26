package solvela.member.verify;

import solvela.base.util.SolvelaStringUtil;

/**
 * 实名信息脱敏。
 *
 * <p><b>为什么脱敏在服务端做，不在前端做</b>：前端脱敏意味着明文已经过网到了浏览器 ——
 * F12 一开、或者抓一次响应，就是全量身份证号。列表接口<b>根本不该下发明文</b>。
 * 需要看完整信息的只有「审核这一条」那个动作，走单独的详情接口。
 *
 * <p>脱敏和加密解决的不是同一件事：{@code PiiTypeHandler} 的加密防的是静态泄露
 * （库被脱、备份被拷、DBA 直接 select），这里的脱敏防的是<b>有应用权限的人一屏看到几十个身份证</b>。
 * 两者都需要。
 *
 * @Date 2026-08-23
 */
public final class MemberVerifyMask {

    private MemberVerifyMask() {
    }

    /**
     * 姓名：保留首字与末字，中间打码。
     *
     * <ul>
     *   <li>张三 → 张*</li>
     *   <li>张三丰 → 张*丰</li>
     *   <li>欧阳修文 → 欧**文</li>
     * </ul>
     *
     * <p>两个字时打掉末字而不是中间（没有中间）—— 保留「张*」仍然能让运营在列表里
     * 和会员账号对上号，这正是列表需要姓名的全部理由。
     */
    public static String maskName(String name) {
        if (SolvelaStringUtil.isEmpty(name)) {
            return name;
        }
        int length = name.codePointCount(0, name.length());
        if (length == 1) {
            return name;
        }
        if (length == 2) {
            return SolvelaStringUtil.hide(name, 1, 2);
        }
        return SolvelaStringUtil.hide(name, 1, length - 1);
    }

    /**
     * 身份证：保留前 6 位（行政区划）与后 4 位，中间打码。
     *
     * <p>330102********1234
     *
     * <p>保留前 6 位是有用途的：运营核对归属地、排查「同一地区批量实名」时看的就是这一段；
     * 后 4 位用于和用户口头核对。中间那 8 位是出生日期 + 顺序码，恰好是最能定位到个人的部分。
     */
    public static String maskIdCard(String idCard) {
        if (SolvelaStringUtil.isEmpty(idCard)) {
            return idCard;
        }
        int length = idCard.codePointCount(0, idCard.length());
        // 长度不对的（脏数据、非大陆证件）一律整体打码，不去猜它的结构
        if (length < 11) {
            return SolvelaStringUtil.hide(idCard, 0, length);
        }
        return SolvelaStringUtil.hide(idCard, 6, length - 4);
    }
}
