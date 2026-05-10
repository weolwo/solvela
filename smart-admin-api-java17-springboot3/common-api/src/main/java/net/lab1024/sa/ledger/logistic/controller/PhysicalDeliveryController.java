package net.lab1024.sa.ledger.logistic.controller;

import net.lab1024.sa.base.common.domain.ValidateList;
import net.lab1024.sa.ledger.logistic.domain.entity.PhysicalDelivery;
import net.lab1024.sa.ledger.logistic.domain.form.PhysicalDeliveryAddForm;
import net.lab1024.sa.ledger.logistic.domain.form.PhysicalDeliveryQueryForm;
import net.lab1024.sa.ledger.logistic.domain.form.PhysicalDeliveryUpdateForm;
import net.lab1024.sa.ledger.logistic.domain.vo.PhysicalDeliveryVO;
import net.lab1024.sa.ledger.logistic.service.PhysicalDeliveryService;
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
 * 发货物流表 Controller
 *
 * @Author weolwo
 * @Date 2026-04-19 00:03:01
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "发货物流表")
@RequestMapping("/physicalDelivery")
public class PhysicalDeliveryController {

    private final PhysicalDeliveryService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission(":query")
    public ResponseDTO<PageResult<PhysicalDeliveryVO>> queryPage(@RequestBody @Valid PhysicalDeliveryQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission(":add")
    public ResponseDTO<String> add(@RequestBody @Valid PhysicalDeliveryAddForm addForm) {
        return Service.add(addForm);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission(":update")
    public ResponseDTO<String> update(@RequestBody @Valid PhysicalDeliveryUpdateForm updateForm) {
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
