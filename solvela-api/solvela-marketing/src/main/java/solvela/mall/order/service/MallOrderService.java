package solvela.mall.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import solvela.base.common.domain.PageResult;
import solvela.base.common.domain.ResponseDTO;
import solvela.base.common.util.SolvelaPageUtil;
import solvela.mall.constant.MallConst;
import solvela.mall.order.dao.MallOrderDao;
import solvela.mall.order.domain.form.MallOrderQueryForm;
import solvela.mall.order.domain.vo.MallOrderRankVO;
import solvela.mall.order.domain.vo.MallOrderStatVO;
import solvela.mall.order.domain.vo.MallOrderVO;
import solvela.member.service.MemberService;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商城-兑换订单 Service
 *
 * <p><b>没有增删改</b>：订单由 C 端下单链路创建、由履约链路推进状态。
 * 后台凭空插一条订单会绕过扣积分、锁库存、限兑计数三件事，造出来的是一个
 * 账对不上的孤单；改状态更危险 —— 状态机是履约链路的，手工拨一下不会真的去发货。
 * 生成器留的 add / update 已删除。
 *
 * @Author weolwo
 * @Date 2026-08-22 19:35:46
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MallOrderService {

    private final MallOrderDao mallOrderDao;
    /** 账号 → 会员号的唯一翻译入口 */
    private final MemberService memberService;

    /**
     * 分页查询。
     */
    public PageResult<MallOrderVO> queryPage(MallOrderQueryForm queryForm) {
        if (!resolveMemberId(queryForm)) {
            // 账号查不到对应会员：正确答案是「没有订单」，不是「全部订单」。
            // 不处理的话，运营输错一个字母会看到全量订单，而他以为那是这个人的
            return emptyPage(queryForm);
        }
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<MallOrderVO> list = mallOrderDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }

    /**
     * 统计 + 兑换商品排行。<b>与列表共用同一套查询条件</b> ——
     * 顶部筛选改了统计跟着变，两套条件的话运营会看到「统计说 100 单、列表只有 3 条」。
     */
    public ResponseDTO<MallOrderStatVO> queryStat(MallOrderQueryForm queryForm) {
        if (!resolveMemberId(queryForm)) {
            return ResponseDTO.ok(emptyStat());
        }
        MallOrderStatVO stat = mallOrderDao.queryStat(queryForm);
        stat = stat == null ? emptyStat() : normalize(stat);

        int topN = queryForm.getRankTopN() == null ? MallConst.RANK_TOP_N : queryForm.getRankTopN();
        topN = Math.min(Math.max(topN, 1), MallConst.MAX_RANK_TOP_N);
        stat.setCommodityRank(mallOrderDao.queryCommodityRank(queryForm, topN));
        return ResponseDTO.ok(stat);
    }

    /**
     * 把账号换算成会员号。
     *
     * <p>🔴 <b>绝不直接用 member_name 查</b>：那一列是展示快照，DDL 注释写着「不要用于查询」，
     * 身上没有索引，而给它建索引会让关联键悄悄退回 member_name。
     *
     * @return false 表示「填了账号但查无此人」，调用方应当直接返回空结果
     */
    private boolean resolveMemberId(MallOrderQueryForm queryForm) {
        String memberName = StringUtils.trimToNull(queryForm.getMemberName());
        if (memberName == null) {
            return true;
        }
        Long memberId = memberService.getMemberId(memberName);
        if (memberId == null) {
            return false;
        }
        queryForm.setMemberId(memberId);
        return true;
    }

    /** PageResult 没有静态工厂，手工拼一个空页 */
    private PageResult<MallOrderVO> emptyPage(MallOrderQueryForm queryForm) {
        PageResult<MallOrderVO> result = new PageResult<>();
        result.setPageNum(queryForm.getPageNum() == null ? 1L : queryForm.getPageNum());
        result.setPageSize(queryForm.getPageSize() == null ? 10L : queryForm.getPageSize());
        result.setTotal(0L);
        result.setPages(0L);
        result.setList(List.of());
        result.setEmptyFlag(true);
        return result;
    }

    private MallOrderStatVO emptyStat() {
        return normalize(new MallOrderStatVO());
    }

    /** SUM/COUNT 在零行时返回 null，直接下发会让前端把「0」渲染成空白 */
    private MallOrderStatVO normalize(MallOrderStatVO stat) {
        stat.setOrderCount(nullToZero(stat.getOrderCount()));
        stat.setMemberCount(nullToZero(stat.getMemberCount()));
        stat.setQuantitySum(nullToZero(stat.getQuantitySum()));
        stat.setFinishedCount(nullToZero(stat.getFinishedCount()));
        stat.setProcessingCount(nullToZero(stat.getProcessingCount()));
        stat.setCancelledCount(nullToZero(stat.getCancelledCount()));
        stat.setFailedCount(nullToZero(stat.getFailedCount()));
        stat.setPayPointsSum(nullToZero(stat.getPayPointsSum()));
        stat.setPayCashSum(stat.getPayCashSum() == null ? BigDecimal.ZERO : stat.getPayCashSum());
        if (stat.getCommodityRank() == null) {
            stat.setCommodityRank(List.of());
        }
        return stat;
    }

    private static long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
