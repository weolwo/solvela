package net.lab1024.sa.ledger.wallet.controller;

import net.lab1024.sa.base.common.domain.ValidateList;
import net.lab1024.sa.ledger.wallet.domain.entity.MemberWallet;
import net.lab1024.sa.ledger.wallet.domain.form.MemberWalletAddForm;
import net.lab1024.sa.ledger.wallet.domain.form.MemberWalletQueryForm;
import net.lab1024.sa.ledger.wallet.domain.form.MemberWalletUpdateForm;
import net.lab1024.sa.ledger.wallet.domain.vo.MemberWalletVO;
import net.lab1024.sa.ledger.wallet.service.MemberWalletService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.domain.PageResult;
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
    @SaCheckPermission(":query")
    public ResponseDTO<PageResult<MemberWalletVO>> queryPage(@RequestBody @Valid MemberWalletQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission(":add")
    public ResponseDTO<String> add(@RequestBody @Valid MemberWalletAddForm addForm) {
        return Service.add(addForm);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission(":update")
    public ResponseDTO<String> update(@RequestBody @Valid MemberWalletUpdateForm updateForm) {
        return Service.update(updateForm);
    }

    @Operation(summary = "批量删除")
    @PostMapping("/batchDelete")
    @SaCheckPermission(":delete")
    public ResponseDTO<String> batchDelete(@RequestBody ValidateList<Long> idList) {
        return Service.batchDelete(idList);
    }

    @Operation(summary = "单个删除")
    @GetMapping("/delete/{id}")
    @SaCheckPermission(":delete")
    public ResponseDTO<String> batchDelete(@PathVariable Long id) {
        return Service.delete(id);
    }
}
