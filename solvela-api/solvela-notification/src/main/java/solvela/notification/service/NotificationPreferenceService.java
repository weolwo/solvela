package solvela.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.enums.NotificationCategoryEnum;
import solvela.notification.MemberNotificationPreference;
import solvela.notification.dao.MemberNotificationPreferenceDao;

/**
 * 免打扰偏好：这个用户还收不收这一类通知。
 *
 * <h3>🔴 默认是「收」，查不到记录不等于关闭</h3>
 * 偏好行是<b>懒创建</b>的（用户第一次改设置才 insert），所以绝大多数用户压根没有这一行。
 * 把「查不到」当成关闭，表现是<b>上线当天全体存量用户再也收不到任何通知</b>，
 * 而且不报错、没人发现 —— 这是这个类里唯一一个真正危险的判断。
 *
 * <h3>SYSTEM 永远放行</h3>
 * 账号被冻结、服务条款变更这类东西不该能被静音。这个判断走
 * {@link NotificationCategoryEnum#isMutable()}，而不是在这里硬编码一个
 * {@code if (category == SYSTEM)} —— 加新分类时，「能不能关」是跟着分类定义走的。
 *
 * @Author alaric
 * @Date 2026-09-14
 * @Copyright weolwo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final MemberNotificationPreferenceDao memberNotificationPreferenceDao;

    /**
     * 这个用户现在接不接收这一类通知。
     *
     * @return true 表示该发。任何异常、任何查不到，都返回 true
     */
    public boolean accepts(Long memberId, NotificationCategoryEnum category) {
        if (category == null || !category.isMutable()) {
            // 不可关的分类（SYSTEM）直接放行，连库都不用查
            return true;
        }
        try {
            MemberNotificationPreference preference = memberNotificationPreferenceDao.selectById(memberId);
            if (preference == null) {
                // 🔴 没有偏好行 = 从没改过设置 = 全部默认开。见类注释
                return true;
            }
            return switch (category) {
                case TRADE -> isEnabled(preference.getTradeEnabled());
                case MARKETING -> isEnabled(preference.getMarketingEnabled());
                // SYSTEM 在上面就返回了，这里只是让 switch 穷尽
                case SYSTEM -> true;
            };
        } catch (Exception e) {
            // 查偏好失败就放行：宁可多发一条用户关掉过的营销通知，
            // 也不要因为一次数据库抖动把发货通知吞掉
            log.error("【通知偏好】查询失败，按「接收」处理。会员:{} 分类:{}", memberId, category, e);
            return true;
        }
    }

    /**
     * 用户改设置。
     */
    public void save(Long memberId, boolean tradeEnabled, boolean marketingEnabled) {
        memberNotificationPreferenceDao.upsert(memberId, tradeEnabled ? 1 : 0, marketingEnabled ? 1 : 0);
    }

    /**
     * 读设置给前端展示。没有记录时返回一个「全开」的默认对象，
     * 而不是 null —— 让端上少写一个判空分支，也少一次「null 该显示成开还是关」的犹豫。
     */
    public MemberNotificationPreference get(Long memberId) {
        MemberNotificationPreference preference = memberNotificationPreferenceDao.selectById(memberId);
        if (preference != null) {
            return preference;
        }
        MemberNotificationPreference defaults = new MemberNotificationPreference();
        defaults.setMemberId(memberId);
        defaults.setTradeEnabled(1);
        defaults.setMarketingEnabled(1);
        return defaults;
    }

    /** null 也当成开：列是后加的，存量行上可能是 null */
    private boolean isEnabled(Integer flag) {
        return flag == null || flag == 1;
    }
}
