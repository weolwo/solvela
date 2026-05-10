package net.lab1024.sa.ledger.transaction.controller;

import net.lab1024.sa.base.common.domain.ValidateList;
import net.lab1024.sa.ledger.transaction.domain.entity.MemberAssetTransaction;
import net.lab1024.sa.ledger.transaction.domain.form.MemberAssetTransactionAddForm;
import net.lab1024.sa.ledger.transaction.domain.form.MemberAssetTransactionQueryForm;
import net.lab1024.sa.ledger.transaction.domain.form.MemberAssetTransactionUpdateForm;
import net.lab1024.sa.ledger.transaction.domain.vo.MemberAssetTransactionVO;
import net.lab1024.sa.ledger.transaction.service.MemberAssetTransactionService;
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
    @SaCheckPermission(":query")
    public ResponseDTO<PageResult<MemberAssetTransactionVO>> queryPage(@RequestBody @Valid MemberAssetTransactionQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission(":add")
    public ResponseDTO<String> add(@RequestBody @Valid MemberAssetTransactionAddForm addForm) {
        return Service.add(addForm);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission(":update")
    public ResponseDTO<String> update(@RequestBody @Valid MemberAssetTransactionUpdateForm updateForm) {
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
