package solvela.member.verify.manager;

import solvela.member.MemberVerify;
import solvela.member.verify.dao.MemberVerifyDao;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

/**
 * 会员实名信息（敏感，与主表分离）  Manager
 *
 * @Author weolwo
 * @Date 2026-08-22 21:00:09
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MemberVerifyManager extends ServiceImpl<MemberVerifyDao, MemberVerify> {


}
