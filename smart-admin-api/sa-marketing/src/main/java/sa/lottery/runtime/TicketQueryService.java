package sa.lottery.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sa.base.common.domain.ResponseDTO;
import sa.lottery.config.domain.entity.LotteryConfig;
import sa.lottery.config.service.LotteryConfigService;
import sa.lottery.engine.FpeCipher;
import sa.lottery.engine.FpeCipherFactory;
import sa.lottery.record.dao.LotteryRecordDao;
import sa.lottery.record.domain.entity.LotteryRecord;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 号码查询与验真。
 *
 * <p>验真是 FPE 方案的直接红利：号码是可逆的，给定一个号码能反解出它对应的游标，
 * 于是「这个号是不是本期真实发出的」不需要查库就能判断个大概，再配合记录与签名可以完全定案。
 * 号池方案做不到这件事 —— 那时候只能查表，查不到就只能说「没找到」，无法区分
 * 「用户记错了」「号码是伪造的」「记录被人删了」。
 *
 * @Author alaric
 * @Date 2026-07-28
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class TicketQueryService {

    private final LotteryConfigService lotteryConfigService;
    private final LotteryRecordDao lotteryRecordDao;
    private final FpeCipherFactory fpeCipherFactory;
    private final TicketSignService ticketSignService;

    /**
     * 我的号码。issueNo 为空则返回该玩法下的全部期。
     * 排序由 SQL 保证：奖级升序（一等奖在前、未中奖的 99 沉底），同奖级按最新在前
     */
    public ResponseDTO<List<LotteryRecord>> myTickets(String lotteryCode, String issueNo, String memberName) {
        return ResponseDTO.ok(lotteryRecordDao.selectMyTickets(lotteryCode, issueNo, memberName));
    }

    /**
     * 号码验真，三层依次判定，把「查不到」拆成几种可区分的情况：
     * <ol>
     *   <li>格式与域：号码长度对不对、是不是纯数字；</li>
     *   <li>反解：游标是否落在本期已发行的区间内 —— 落在区间外说明这号码根本不可能被发出来（伪造）；</li>
     *   <li>落库：记录在不在、签名对不对 —— 反解合法但查无记录，说明记录被删或尚未发到这个游标。</li>
     * </ol>
     */
    public ResponseDTO<String> verify(String lotteryCode, String issueNo, String ticketNumber) {
        LotteryConfig config = lotteryConfigService.getByLotteryCode(lotteryCode);
        if (config == null) {
            return ResponseDTO.userErrorParam("彩票玩法不存在：" + lotteryCode);
        }

        long sequenceNo;
        try {
            FpeCipher cipher = fpeCipherFactory.create(lotteryCode, issueNo, config.getNumberLength());
            sequenceNo = cipher.decrypt(ticketNumber);
        } catch (RuntimeException e) {
            return ResponseDTO.ok("❌ 号码格式非法：" + e.getMessage());
        }

        if (sequenceNo >= config.getTotalCount()) {
            return ResponseDTO.ok("❌ 号码不可能由本期发出（反解游标 " + sequenceNo
                    + " 超出本期发行量 " + config.getTotalCount() + "），疑似伪造");
        }

        List<LotteryRecord> list = lotteryRecordDao.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LotteryRecord>()
                        .eq(LotteryRecord::getLotteryCode, lotteryCode)
                        .eq(LotteryRecord::getIssueNo, issueNo)
                        .eq(LotteryRecord::getTicketNumber, ticketNumber));
        if (list.isEmpty()) {
            return ResponseDTO.ok("⚠️ 号码格式合法（对应游标 " + sequenceNo + "），但库中无发放记录 —— "
                    + "可能该游标尚未发出，或记录已被删除");
        }
        LotteryRecord record = list.get(0);
        if (!ticketSignService.verify(record)) {
            log.error("[彩票验真] 签名不匹配，记录可能被直接改库 recordId={}, ticketNumber={}",
                    record.getId(), ticketNumber);
            return ResponseDTO.ok("🔴 记录存在但签名校验失败，数据可能被篡改，请人工核查（记录ID " + record.getId() + "）");
        }
        return ResponseDTO.ok("✅ 号码真实有效：归属 " + record.getMemberName()
                + "，游标 " + record.getSequenceNo() + "，领取于 " + record.getObtainTime());
    }
}
