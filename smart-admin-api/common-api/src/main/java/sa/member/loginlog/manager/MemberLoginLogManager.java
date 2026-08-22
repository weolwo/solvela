package sa.member.loginlog.manager;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sa.member.loginlog.dao.MemberLoginLogDao;
import sa.member.loginlog.domain.entity.MemberLoginLog;

/**
 * 会员登录日志（append-only，按月分区）  Manager
 *
 * @Author weolwo
 * @Date 2026-08-22 20:58:39
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MemberLoginLogManager extends ServiceImpl<MemberLoginLogDao, MemberLoginLog> {


}
