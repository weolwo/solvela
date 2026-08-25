package solvela.member.manager;

import solvela.member.dao.MemberDao;
import solvela.member.domain.entity.Member;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
/**
 * 会员主表  Manager
 *
 * @Author weolwo
 * @Date 2026-08-22 19:39:08
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MemberManager extends ServiceImpl<MemberDao, Member> {


}
