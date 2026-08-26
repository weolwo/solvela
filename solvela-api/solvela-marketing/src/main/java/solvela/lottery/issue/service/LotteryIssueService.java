package solvela.lottery.issue.service;

import java.time.LocalDateTime;
import java.util.List;

import solvela.base.util.SolvelaCollectionUtil;
import solvela.lottery.issue.dao.LotteryIssueDao;
import solvela.lottery.issue.manager.LotteryIssueManager;
import solvela.lottery.config.service.LotteryConfigService;
import solvela.lottery.issue.domain.entity.LotteryIssue;
import solvela.lottery.issue.domain.form.LotteryIssueAddForm;
import solvela.lottery.issue.domain.form.LotteryIssueQueryForm;
import solvela.lottery.issue.domain.form.LotteryIssueUpdateForm;
import solvela.lottery.issue.domain.vo.LotteryIssueOverviewVO;
import solvela.lottery.issue.domain.vo.LotteryIssueVO;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.domain.ResponseDTO;
import solvela.base.domain.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

/**
 * 期号配置 Service
 *
 * @Author weolwo
 * @Date 2026-05-09 16:54:51
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class LotteryIssueService {

    private final LotteryIssueDao lotteryIssueDao;
    private final LotteryIssueManager lotteryIssueManager;
    private final LotteryConfigService lotteryConfigService;

    /**
     * 期号状态：0-待开奖（可售卖、可编辑、可删除）
     */
    private static final Integer STATUS_WAIT = 0;

    /**
     * 分页查询
     */
    public PageResult<LotteryIssueVO> queryPage(LotteryIssueQueryForm queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<LotteryIssueVO> list = lotteryIssueDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }

    /**
     * 巡检概览：列表页顶部四张卡片。
     *
     * 口径与列表的派生字段共用同一份 SQL 表达式，所以点卡片筛出来的行数
     * 必然等于卡片上的数字 —— 两处各写一套统计逻辑迟早对不上。
     */
    public LotteryIssueOverviewVO overview(LotteryIssueQueryForm queryForm) {
        return lotteryIssueDao.overview(queryForm);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(LotteryIssueAddForm addForm) {
        if (lotteryConfigService.getByLotteryCode(addForm.getLotteryCode()) == null) {
            return ResponseDTO.userErrorParam("彩票玩法不存在：" + addForm.getLotteryCode());
        }
        if (existsIssueNo(addForm.getLotteryCode(), addForm.getIssueNo())) {
            return ResponseDTO.userErrorParam("期号已存在：" + addForm.getIssueNo());
        }
        String timeError = checkSaleWindow(addForm.getSaleStartTime(), addForm.getSaleEndTime());
        if (timeError != null) {
            return ResponseDTO.userErrorParam(timeError);
        }
        LotteryIssue lotteryIssue = SolvelaBeanUtil.copy(addForm, LotteryIssue.class);
        lotteryIssue.setSoldCount(0);
        lotteryIssue.setStatus(STATUS_WAIT);
        lotteryIssueDao.insert(lotteryIssue);
        return ResponseDTO.ok();
    }

    /**
     * 更新。
     *
     * ⚠️ issue_no 与 lottery_code 是 FPE 的密钥派生素材（key = HMAC(secret, lotteryCode|issueNo)），
     * 改动它们等于换了一套加密映射：已发号码再也反解不回游标、签名全部失效、
     * 新号码还可能与历史号码重复。所以这两个字段一律忽略前端传值，只允许改时间。
     */
    public ResponseDTO<String> update(LotteryIssueUpdateForm updateForm) {
        LotteryIssue existed = lotteryIssueDao.selectById(updateForm.getId());
        if (existed == null) {
            return ResponseDTO.userErrorParam("期号不存在");
        }
        if (!STATUS_WAIT.equals(existed.getStatus())) {
            return ResponseDTO.userErrorParam("该期已开奖或正在核销，不能再修改");
        }
        String timeError = checkSaleWindow(updateForm.getSaleStartTime(), updateForm.getSaleEndTime());
        if (timeError != null) {
            return ResponseDTO.userErrorParam(timeError);
        }
        // 已经发过号之后，连售卖开始时间都不该往后挪 —— 那会让「已发出的号」落在窗口之外，自相矛盾
        int sold = existed.getSoldCount() == null ? 0 : existed.getSoldCount();
        if (sold > 0 && updateForm.getSaleStartTime() != null
                && updateForm.getSaleStartTime().isAfter(existed.getSaleStartTime())) {
            return ResponseDTO.userErrorParam("本期已发出 " + sold + " 个号码，售卖开始时间不能再往后调整");
        }

        LotteryIssue update = new LotteryIssue();
        update.setId(existed.getId());
        update.setSaleStartTime(updateForm.getSaleStartTime());
        update.setSaleEndTime(updateForm.getSaleEndTime());
        update.setPlanDrawTime(updateForm.getPlanDrawTime());
        // lottery_code / issue_no / sold_count / status / winning_number 一律不接受前端值
        lotteryIssueDao.updateById(update);
        return ResponseDTO.ok();
    }

    public boolean existsIssueNo(String lotteryCode, String issueNo) {
        return lotteryIssueManager.lambdaQuery()
                .eq(LotteryIssue::getLotteryCode, lotteryCode)
                .eq(LotteryIssue::getIssueNo, issueNo).exists();
    }

    /**
     * 售卖窗口合法性，返回 null 表示通过
     */
    private String checkSaleWindow(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        if (start == null || end == null) {
            return "售卖起止时间不能为空";
        }
        if (!end.isAfter(start)) {
            return "售卖结束时间必须晚于开始时间";
        }
        return null;
    }

    /**
     * 已发过号的期号禁止删除：记录还在 t_lottery_record 里，删了期号会让那些记录变成孤儿，
     * 而它们既是用户的凭证、也是开奖核销的依据
     */
    private String checkDeletable(Long id) {
        LotteryIssue issue = lotteryIssueDao.selectById(id);
        if (issue == null) {
            return null;
        }
        int sold = issue.getSoldCount() == null ? 0 : issue.getSoldCount();
        if (sold > 0) {
            return "期号「" + issue.getIssueNo() + "」已发出 " + sold + " 个号码，不能删除";
        }
        if (!STATUS_WAIT.equals(issue.getStatus())) {
            return "期号「" + issue.getIssueNo() + "」已开奖或正在核销，不能删除";
        }
        return null;
    }

    /**
     * 停售（列表页的「禁用」）：把售卖结束时间提前到此刻，立刻停止发号。
     *
     * <p>🔴 期号<b>没有</b>「启用/禁用」这一维。它的 status 是生命周期
     * （0-待开奖 / 1-核销中 / 2-已开奖），不是开关。
     * 而「这一期现在还能不能领号」的判据，运行态 {@code TicketIssueService} 只认三条：
     * 玩法已上线、期号是待开奖、当前时间落在 [sale_start_time, sale_end_time] 内。
     * 再加一个禁用标志位就是第四个判据，和售卖窗口重叠 —— 两个判据一定会漂移。
     * 所以停售就落在窗口上：改的是既有字段，运行态一个字都不用动。
     *
     * <p>可逆：想恢复售卖，把结束时间改回一个未来时刻即可（走编辑）。
     *
     * <p>为什么减 1 秒：结束时间的判据是 {@code now.isAfter(saleEndTime)}，
     * 取严格大于。若把结束时间设成整点的「此刻」，同一秒内进来的请求 isAfter 为 false，
     * 会漏出一个可领号的窗口。止血动作不该留这种缝。
     */
    public ResponseDTO<String> stopSale(Long id) {
        LotteryIssue issue = lotteryIssueDao.selectById(id);
        if (issue == null) {
            return ResponseDTO.userErrorParam("期号不存在");
        }
        if (!STATUS_WAIT.equals(issue.getStatus())) {
            return ResponseDTO.userErrorParam("期号「" + issue.getIssueNo() + "」已开奖或正在核销，本就不再发号");
        }
        // 时间只由数据库产生（铁律 9/10）：售卖窗口的判定用的是 DB 时钟，写入也必须用同一个
        LocalDateTime stopAt = lotteryIssueDao.selectDbNow().minusSeconds(1);
        if (issue.getSaleEndTime() != null && !issue.getSaleEndTime().isAfter(stopAt)) {
            return ResponseDTO.userErrorParam("期号「" + issue.getIssueNo() + "」已经停止发售");
        }

        LotteryIssue update = new LotteryIssue();
        update.setId(issue.getId());
        update.setSaleEndTime(stopAt);
        // 尚未开始售卖的期：把开始时间一并拉到 stopAt，否则会落下 end < start 的自相矛盾数据，
        // 之后再编辑会被 checkSaleWindow 挡住。此时 sold_count 必为 0，拉早开始时间没有副作用
        if (issue.getSaleStartTime() == null || issue.getSaleStartTime().isAfter(stopAt)) {
            update.setSaleStartTime(stopAt);
        }
        lotteryIssueDao.updateById(update);
        return ResponseDTO.ok();
    }

    /**
     * 批量停售。
     *
     * <p>与彩票玩法的批量禁用同构：<b>不</b>做成一个事务里全成或全败 ——
     * 止血动作里混着几个「本来就停售了」是常态，不该把其余的一起回滚掉。
     * 已停售 / 已开奖计入跳过，最后回一句人话汇总。
     */
    public ResponseDTO<String> batchStopSale(List<Long> idList) {
        if (SolvelaCollectionUtil.isEmpty(idList)) {
            return ResponseDTO.ok();
        }
        int success = 0;
        int skipped = 0;
        for (Long id : idList) {
            if (stopSale(id).getOk()) {
                success++;
            } else {
                skipped++;
            }
        }
        return ResponseDTO.ok("已停售 " + success + " 期"
                + (skipped > 0 ? "，跳过 " + skipped + " 期（已停售或已开奖）" : ""));
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id){
            return ResponseDTO.ok();
        }
        String error = checkDeletable(id);
        if (error != null) {
            return ResponseDTO.userErrorParam(error);
        }
        lotteryIssueDao.deleteById(id);
        return ResponseDTO.ok();
    }
}
