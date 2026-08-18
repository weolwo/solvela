package sa.ledger.wallet.controller;

import sa.base.common.domain.ValidateList;
import sa.ledger.wallet.domain.entity.MemberWallet;
import sa.ledger.wallet.domain.form.MemberWalletAddForm;
import sa.ledger.wallet.domain.form.MemberWalletQueryForm;
import sa.ledger.wallet.domain.form.MemberWalletUpdateForm;
import sa.ledger.wallet.domain.vo.MemberWalletStatVO;
import sa.ledger.wallet.domain.vo.MemberWalletVO;
import sa.ledger.stat.domain.form.LedgerStatForm;
import sa.ledger.wallet.service.MemberWalletService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import sa.base.common.domain.ResponseDTO;
import sa.base.common.domain.PageResult;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
/**
 * 会员钱包表 Controller
 *
 * @Author weolwo
 * @Date 2026-04-18 23:56:48
 * @Copyright weolwo
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
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "钱包统计：资产存量（全量）+ 本期变动（默认当天，取自交易明细）")
    @PostMapping("/stat")
    @SaCheckPermission("memberWallet:query")
    public ResponseDTO<MemberWalletStatVO> stat(@RequestBody @Valid LedgerStatForm form) {
        return ResponseDTO.ok(Service.stat(form));
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission("memberWallet:add")
    public ResponseDTO<String> add(@RequestBody @Valid MemberWalletAddForm addForm) {
        return Service.add(addForm);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission("memberWallet:update")
    public ResponseDTO<String> update(@RequestBody @Valid MemberWalletUpdateForm updateForm) {
        return Service.update(updateForm);
    }

    @Operation(summary = "批量删除")
    @PostMapping("/batchDelete")
    @SaCheckPermission("memberWallet:delete")
    public ResponseDTO<String> batchDelete(@RequestBody ValidateList<Long> idList) {
        return Service.batchDelete(idList);
    }

    @Operation(summary = "单个删除")
    @GetMapping("/delete/{id}")
    @SaCheckPermission("memberWallet:delete")
    public ResponseDTO<String> batchDelete(@PathVariable Long id) {
        return Service.delete(id);
    }
}
