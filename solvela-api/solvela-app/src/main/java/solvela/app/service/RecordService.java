package solvela.app.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.app.domain.RecordView;
import solvela.enums.PrizeDispatchStatusEnum;
import solvela.enums.ProposalStatusEnum;
import solvela.marketing.api.PrizeRecordApi;
import solvela.marketing.api.PrizeRecordView;
import solvela.member.api.ProposalRecordApi;
import solvela.member.api.ProposalRecordView;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 「我的记录」的接入层：<b>翻译 + 组装</b>，没有业务逻辑。
 *
 * <h3>三种记录是三件事，刻意不合并成一个列表</h3>
 * <ul>
 *   <li><b>奖励记录</b>（{@code t_prize_log}）—— 我在<b>某个活动</b>里得了什么。
 *       所以它挂在活动专题页上，按 activityCode 过滤</li>
 *   <li><b>优惠记录</b>（{@code t_proposal_record}）—— 平台要发给我什么，
 *       包括还在审批路上的</li>
 *   <li><b>兑换记录</b>（{@code t_mall_order}）—— 我花积分换了什么。见 OrderService</li>
 * </ul>
 * 混成一个「我的记录」曾经是这里的做法，问题是三者的状态机、金额口径、
 * 该显示什么完全不同，合并之后每一条都只能显示最小公约数。
 */
@Service
@RequiredArgsConstructor
public class RecordService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final int RECENT_LIMIT = 20;

    private final PrizeRecordApi prizeRecordApi;
    private final ProposalRecordApi proposalRecordApi;

    /**
     * 奖励记录。{@code activityCode} 为空表示不限活动。
     *
     * <p>活动专题页传活动编码 —— 过滤在服务端做，不是拿最近 20 条回来再筛：
     * 参与多个活动的用户会筛出空列表，而他明明在这个活动里中过奖。
     */
    public List<RecordView> listPrizeRecords(Long memberId, String activityCode) {
        return prizeRecordApi.listRecentRecords(memberId, activityCode, RECENT_LIMIT).stream()
                .map(RecordService::toView)
                .toList();
    }

    /** 优惠记录。就是提案记录 —— 「提案」是运营视角的词，C 端不出现 */
    public List<RecordView> listPromoRecords(Long memberId) {
        return proposalRecordApi.listRecent(memberId, RECENT_LIMIT).stream()
                .map(RecordService::toView)
                .toList();
    }

    /* ---------------- 奖励记录 ---------------- */

    private static RecordView toView(PrizeRecordView record) {
        return new RecordView(
                record.recordId(),
                record.prizeName(),
                statusText(record.status()),
                status(record.status()),
                record.prizeValue(),
                format(record.createTime()));
    }

    private static String statusText(PrizeDispatchStatusEnum status) {
        return switch (status) {
            case WAITING -> "发放中";
            case SUCCESS -> "已到账";
            case FAIL -> "发放失败";
        };
    }

    private static String status(PrizeDispatchStatusEnum status) {
        return switch (status) {
            case WAITING -> "PENDING";
            case SUCCESS -> "DONE";
            case FAIL -> "FAILED";
        };
    }

    /* ---------------- 优惠记录 ---------------- */

    private static RecordView toView(ProposalRecordView record) {
        return new RecordView(
                record.recordId(),
                title(record),
                promoStatusText(record.status()),
                promoStatus(record.status()),
                amount(record),
                format(record.createTime()));
    }

    /**
     * 标题：有资产名就用它，没有就用资产类型兜底。
     *
     * <p>值类资产（积分、现金）本来就没有名字 —— DDL 里 {@code asset_name} 那列
     * 注释写的是「券名/商品名」。给它们显示一个空标题不如显示「积分」。
     */
    private static String title(ProposalRecordView record) {
        if (record.assetName() != null && !record.assetName().isBlank()) {
            return record.assetName();
        }
        return switch (record.assetType() == null ? "" : record.assetType()) {
            case "SCORE" -> "积分";
            case "BALANCE" -> "现金红包";
            case "COUPON" -> "优惠券";
            case "PHYSICAL" -> "实物奖品";
            case "LOTTERY" -> "彩票";
            // 认不出的类型不编一个名字：显示编码至少是可追溯的
            default -> record.assetRef() == null ? "优惠" : record.assetRef();
        };
    }

    /**
     * 数量。<b>积分显示整数，其余保留小数。</b>
     *
     * <p>库里是 {@code decimal(13,4)} 一种类型，但「45000.0000 积分」是错的展示。
     * 判断依据是资产类型，不是「小数位是不是全零」—— 后者会让 10.00 元显示成 10。
     */
    private static String amount(ProposalRecordView record) {
        BigDecimal amount = record.amount();
        if (amount == null) {
            return null;
        }
        if ("SCORE".equals(record.assetType())) {
            return amount.stripTrailingZeros().toBigInteger().toString();
        }
        return amount.stripTrailingZeros().toPlainString();
    }

    /**
     * 提案状态 → 给用户看的话。
     *
     * <h3>🔴 审批环节一律说「处理中」</h3>
     * 「待一审」「待二审」「待执行」是<b>运营视角</b>的说法。对用户，
     * 它们都是同一件事：还没到账，等着。把内部流程摊给用户看，
     * 除了让他去催客服「我的二审为什么还没过」之外没有任何用处。
     *
     * <h3>🔴 风控拦截绝不能说破</h3>
     * {@code RISK_BLOCKED} 说成「未通过」，和「驳回」同一句话。
     * 告诉用户「单笔超限」「频次超限」等于告诉他下次怎么绕过去 ——
     * 而 {@code risk_code} 那一列正是按这几种拦截分类的。
     *
     * <p>用 switch 表达式且<b>不写 default</b>：提案域新增状态时这里编译不过，
     * 而不是悄悄落进「处理中」，让一个失败的单子看起来还在路上。
     */
    private static String promoStatusText(ProposalStatusEnum status) {
        return switch (status) {
            case WAITING, FIRST_REVIEW, SECOND_REVIEW, PENDING_EXECUTE, EXECUTING -> "处理中";
            case SUCCESS -> "已到账";
            // 部分成功对用户是「到了一部分」，说成「已到账」会让他以为全到了
            case PARTIAL_SUCCESS -> "部分到账";
            case FAILED -> "发放失败";
            // 驳回与风控拦截同一句话，见方法注释
            case REJECTED, RISK_BLOCKED -> "未通过";
        };
    }

    private static String promoStatus(ProposalStatusEnum status) {
        return switch (status) {
            case WAITING, FIRST_REVIEW, SECOND_REVIEW, PENDING_EXECUTE, EXECUTING -> "PENDING";
            case SUCCESS, PARTIAL_SUCCESS -> "DONE";
            case FAILED, REJECTED, RISK_BLOCKED -> "FAILED";
        };
    }

    /* ---------------- 共用 ---------------- */

    private static String format(LocalDateTime time) {
        return time == null ? null : time.format(DATE_TIME);
    }
}
