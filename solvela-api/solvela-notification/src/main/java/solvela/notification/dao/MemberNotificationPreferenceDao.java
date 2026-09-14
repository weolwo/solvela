package solvela.notification.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import solvela.notification.MemberNotificationPreference;

/**
 * 会员通知偏好 Dao。
 *
 * @Author alaric
 * @Date 2026-09-14
 * @Copyright weolwo
 */
@Mapper
public interface MemberNotificationPreferenceDao extends BaseMapper<MemberNotificationPreference> {

    /**
     * upsert：用户改设置时调。
     *
     * <p>用 {@code INSERT ... ON DUPLICATE KEY UPDATE} 而不是「先查后写」——
     * 后者在用户两个端同时改设置时会插两行（虽然主键会拦住，但报出来的是
     * 一个吓人的 DuplicateKeyException，而这只是一次正常的并发）。
     */
    int upsert(@Param("memberId") Long memberId,
               @Param("tradeEnabled") Integer tradeEnabled,
               @Param("marketingEnabled") Integer marketingEnabled);
}
