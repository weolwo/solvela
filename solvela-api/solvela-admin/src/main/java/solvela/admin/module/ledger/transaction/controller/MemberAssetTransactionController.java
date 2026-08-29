package solvela.admin.module.ledger.transaction.controller;

import solvela.web.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.domain.PageResult;
import solvela.base.util.SolvelaBeanUtil;
import solvela.ledger.transaction.domain.dto.MemberAssetTransactionDTO;
import solvela.ledger.transaction.domain.query.MemberAssetTransactionQuery;
import solvela.admin.module.ledger.stat.domain.form.LedgerStatForm;
import solvela.ledger.stat.domain.query.LedgerStatQuery;
import solvela.admin.module.ledger.transaction.domain.form.MemberAssetTransactionQueryForm;
import solvela.ledger.transaction.domain.dto.MemberAssetTransactionStatDTO;
import solvela.admin.module.ledger.transaction.domain.vo.MemberAssetTransactionVO;
import solvela.ledger.transaction.service.MemberAssetTransactionService;

/**
 * 交易明细表 Controller
 *
 * @Author weolwo
 * @Date 2026-04-18 23:49:03
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "交易明细表")
@RequestMapping("/memberAssetTransaction")
public class MemberAssetTransactionController {

    private final MemberAssetTransactionService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @RequiresPermission("memberAssetTransaction:query")
    public PageResult<MemberAssetTransactionVO> queryPage(@RequestBody @Valid MemberAssetTransactionQueryForm queryForm) {
        MemberAssetTransactionQuery query = SolvelaBeanUtil.copy(queryForm, MemberAssetTransactionQuery.class);
        PageResult<MemberAssetTransactionDTO> page = Service.queryPage(query);
        return SolvelaPageUtil.convert2PageResult(page, MemberAssetTransactionVO.class);
    }

    @Operation(summary = "交易统计：按资产类型的收支与净额、业务类型分布、账务体检（时间范围默认当天）")
    @PostMapping("/stat")
    @RequiresPermission("memberAssetTransaction:query")
    public MemberAssetTransactionStatDTO stat(@RequestBody @Valid LedgerStatForm form) {
        return Service.stat(
                SolvelaBeanUtil.copy(form, LedgerStatQuery.class));
    }

    /*
     * 生成器给的「添加 / 更新 / 批量删除 / 单个删除」四个接口已整组移除（v3.69.0）。
     *
     * 这张表是账务流水，不是业务配置：
     *   · 改一行等于让账面和真实发生过的事脱节，而对账正是以它为准；
     *   · 原先的 delete 还是<b>物理删除</b>（mapper 里就是 DELETE FROM），删完不留痕，
     *     事后连"少了什么"都查不出来。
     *
     * 前端本来就没调用这几个接口（api 封装里只留了查询），但 HTTP 出口一直是活的 ——
     * 拿到 token 就能打。真需要人工订正，走提案链路或 DBA 流程并留痕。
     */
}
