package solvela.notification.manager;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.notification.MemberNotification;
import solvela.notification.dao.MemberNotificationDao;

/**
 * 会员通知 Manager
 *
 * @Author alaric
 * @Date 2026-09-14
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MemberNotificationManager extends ServiceImpl<MemberNotificationDao, MemberNotification> {

}
