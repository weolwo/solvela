package solvela.member.loginlog.manager;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.member.loginlog.dao.MemberLoginLogDao;
import solvela.member.MemberLoginLog;

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
