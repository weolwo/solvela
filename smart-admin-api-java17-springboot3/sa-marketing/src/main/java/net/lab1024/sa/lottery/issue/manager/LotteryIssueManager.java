package net.lab1024.sa.lottery.issue.manager;

import net.lab1024.sa.lottery.issue.domain.entity.LotteryIssue;
import net.lab1024.sa.lottery.issue.dao.LotteryIssueDao;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
/**
 * 期号配置  Manager
 *
 * @Author weolwo
 * @Date 2026-05-09 16:54:51
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class LotteryIssueManager extends ServiceImpl<LotteryIssueDao, LotteryIssue> {


}
