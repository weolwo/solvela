package solvela.admin.module.ledger.logistic.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import solvela.base.domain.PageResult;
import solvela.base.domain.ResponseDTO;
import solvela.base.domain.ValidateList;
import solvela.base.sonicexcel.SolvelaExcelUtil;
import solvela.ledger.logistic.domain.form.*;
import solvela.ledger.logistic.domain.vo.PhysicalDeliveryStatVO;
import solvela.ledger.logistic.domain.vo.PhysicalDeliveryVO;
import solvela.ledger.stat.domain.form.LedgerStatForm;
import solvela.ledger.logistic.service.PhysicalDeliveryService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;

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
    @SaCheckPermission("physicalDelivery:query")
    public ResponseDTO<PageResult<PhysicalDeliveryVO>> queryPage(@RequestBody @Valid PhysicalDeliveryQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "发货统计：本期新增（默认当天）+ 待发货积压与收件信息体检（积压是全量）")
    @PostMapping("/stat")
    @SaCheckPermission("physicalDelivery:query")
    public ResponseDTO<PhysicalDeliveryStatVO> stat(@RequestBody @Valid LedgerStatForm form) {
        return ResponseDTO.ok(Service.stat(form));
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission("physicalDelivery:add")
    public ResponseDTO<String> add(@RequestBody @Valid PhysicalDeliveryAddForm addForm) {
        return Service.add(addForm);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission("physicalDelivery:update")
    public ResponseDTO<String> update(@RequestBody @Valid PhysicalDeliveryUpdateForm updateForm) {
        return Service.update(updateForm);
    }

    @Operation(summary = "批量删除")
    @PostMapping("/batchDiscard")
    @SaCheckPermission("physicalDelivery:discard")
    public ResponseDTO<String> batchDiscard(@RequestBody ValidateList<Long> idList) {
        return Service.batchDiscard(idList);
    }

    @Operation(summary = "单个删除")
    @GetMapping("/discard/{id}")
    @SaCheckPermission("physicalDelivery:discard")
    public ResponseDTO<String> discard(@PathVariable Long id) {
        return Service.discard(id);
    }

    // ------------------------------------------------------------------ Excel 导入

    @Operation(summary = "导入模板下载：新增履约单")
    @GetMapping("/importAddTemplate")
    @SaCheckPermission("physicalDelivery:import")
    public void importAddTemplate(HttpServletResponse response) throws IOException {
        // 空数据 = 只有表头（带下拉）的模板；表头由 Form 上的 @SonicTitle 单一来源生成，
        // 不会出现"字段改了模板没改"的漂移
        SolvelaExcelUtil.exportExcel(response, "发货物流-新增模板.xlsx", "新增履约单",
                PhysicalDeliveryImportForm.class, Collections.emptyList());
    }

    @Operation(summary = "导入模板下载：回填物流")
    @GetMapping("/importShipTemplate")
    @SaCheckPermission("physicalDelivery:import")
    public void importShipTemplate(HttpServletResponse response) throws IOException {
        SolvelaExcelUtil.exportExcel(response, "发货物流-回填模板.xlsx", "回填物流",
                PhysicalDeliveryShipImportForm.class, Collections.emptyList());
    }

    @Operation(summary = "导入：新增履约单")
    @PostMapping("/importAdd")
    @SaCheckPermission("physicalDelivery:import")
    public ResponseDTO<String> importAdd(@RequestParam MultipartFile file) {
        return Service.importAdd(file);
    }

    @Operation(summary = "导入：回填物流")
    @PostMapping("/importShip")
    @SaCheckPermission("physicalDelivery:import")
    public ResponseDTO<String> importShip(@RequestParam MultipartFile file) {
        return Service.importShip(file);
    }
}
