package solvela.app.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.app.domain.RecordView;
import solvela.enums.PrizeDispatchStatusEnum;
import solvela.marketing.api.PrizeRecordApi;
import solvela.marketing.api.PrizeRecordView;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 我的记录的接入层：<b>翻译 + 组装</b>，没有业务逻辑。
 *
 * <h3>🔴 failReason 不下发</h3>
 * 那是<b>内部原因</b>（「资产账户冻结」「提案被驳回」「奖池库存不足」），
 * 对用户既没有可操作性，又暴露了内部结构。用户要知道的只有「发放失败，请联系客服」——
 * 真正的原因在运营后台看得到，客服按记录 id 就能查。
 */
@Service
@RequiredArgsConstructor
public class RecordService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 「我的」页那个列表要几条。再多用户也不会往下翻，真要看全部是另一页的事 */
    private static final int RECENT_LIMIT = 20;

    private final PrizeRecordApi prizeRecordApi;

    /**
     * 我最近的记录。没有记录时返回空列表 —— 新用户就是这个状态。
     *
     * @param memberId 会员号，<b>由控制器从登录态取</b>
     */
    public List<RecordView> listRecent(Long memberId) {
        return prizeRecordApi.listRecentRecords(memberId, RECENT_LIMIT).stream()
                .map(RecordService::toView)
                .toList();
    }

    private static RecordView toView(PrizeRecordView record) {
        return new RecordView(
                record.recordId(),
                record.prizeName(),
                statusText(record.status()),
                status(record.status()),
                record.prizeValue(),
                format(record.createTime()));
    }

    /**
     * 状态 → 给用户看的一句话。
     *
     * <p>用 switch 表达式不给兜底：奖品域新增一个派发状态时<b>这里编译不过</b>，
     * 而不是悄悄落进 default 显示成「发放中」—— 那会让一个失败的发奖看着像还在路上，
     * 用户就不会来找客服。
     */
    private static String statusText(PrizeDispatchStatusEnum status) {
        return switch (status) {
            case WAITING -> "发放中";
            case SUCCESS -> "已发放";
            // 刻意不带原因：failReason 是内部原因，见类注释
            case FAIL -> "发放失败，请联系客服";
        };
    }

    /** 只给前端选颜色用，所以是三个稳定的短码，不是内部枚举名 */
    private static String status(PrizeDispatchStatusEnum status) {
        return switch (status) {
            case WAITING -> "PENDING";
            case SUCCESS -> "DONE";
            case FAIL -> "FAILED";
        };
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : time.format(DATE_TIME);
    }
}
