package net.lab1024.sa.lottery.issue.manager;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.lottery.issue.dao.LotteryIssueDao;
import net.lab1024.sa.lottery.issue.domain.entity.LotteryIssue;
import org.springframework.stereotype.Service;

/**
 * 期号配置  Manager
 *
 * @Author weolwo
 * @Date 2026-04-19 11:23:43
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class LotteryIssueManager extends ServiceImpl<LotteryIssueDao, LotteryIssue> {
}
