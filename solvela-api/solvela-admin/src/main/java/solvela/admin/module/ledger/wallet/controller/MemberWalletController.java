package solvela.admin.module.ledger.wallet.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.admin.module.ledger.wallet.domain.form.MemberWalletQueryForm;
import solvela.admin.module.ledger.wallet.domain.vo.MemberWalletVO;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.domain.PageResult;
import solvela.base.domain.ResponseDTO;
import solvela.base.util.SolvelaBeanUtil;
import solvela.ledger.stat.domain.form.LedgerStatForm;
import solvela.ledger.wallet.domain.dto.MemberWalletDTO;
import solvela.ledger.wallet.domain.query.MemberWalletQuery;
import solvela.ledger.wallet.domain.vo.MemberWalletStatVO;
import solvela.ledger.wallet.service.MemberWalletService;

/**
 * 会员钱包（管理端）。
 *
 * <h3>本类是「装配层」的样板</h3>
 * 共享层（common-api）只认领域参数与读模型：入参 {@link MemberWalletQuery}、
 * 出参 {@link MemberWalletDTO}。<b>Form 与 VO 属于管理端，不进共享层</b>，
 * 两侧的转换就发生在这里。
 *
 * <p>C 端将来做「我的钱包」时，写自己的 Form 与 VO、调同一个 service ——
 * 不需要构造管理端的表单，也不会拿到管理端的字段。这就是这一层存在的全部理由。
 *
 * <p>⚠️ 转换<b>不能省</b>。直接把 DTO 返回给前端看似少写一行，代价是
 * {@code version}（乐观锁）与 {@code createBy}/{@code updateBy}（后台运营账号）
 * 一起出去了，而且以后领域层给 DTO 加一列，接口会<b>悄悄</b>多一个字段，没有任何人 review 到。
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "会员钱包表")
@RequestMapping("/memberWallet")
public class MemberWalletController {

    private final MemberWalletService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission("memberWallet:query")
    public ResponseDTO<PageResult<MemberWalletVO>> queryPage(@RequestBody @Valid MemberWalletQueryForm queryForm) {
        MemberWalletQuery query = SolvelaBeanUtil.copy(queryForm, MemberWalletQuery.class);
        PageResult<MemberWalletDTO> page = Service.queryPage(query);
        return ResponseDTO.ok(SolvelaPageUtil.convert2PageResult(page, MemberWalletVO.class));
    }

    /**
     * 统计口径与交易明细页共用一段 SQL，尚未按 Query/DTO 改造 —— 见类注释里的分层说明。
     * 改造它要连带动 transaction 子域（{@code MemberAssetTransactionStatVO.AssetFlowVO}），
     * 所以单独排一轮，不跟分页查询混在一起。
     */
    @Operation(summary = "钱包统计：资产存量（全量）+ 本期变动（默认当天，取自交易明细）")
    @PostMapping("/stat")
    @SaCheckPermission("memberWallet:query")
    public ResponseDTO<MemberWalletStatVO> stat(@RequestBody @Valid LedgerStatForm form) {
        return ResponseDTO.ok(Service.stat(form));
    }
}
