package net.lab1024.sa.draw.poolconfig.controller;

import net.lab1024.sa.base.common.domain.ValidateList;
import net.lab1024.sa.draw.poolconfig.domain.entity.PrizePoolConfig;
import net.lab1024.sa.draw.poolconfig.domain.form.PrizePoolConfigAddForm;
import net.lab1024.sa.draw.poolconfig.domain.form.PrizePoolConfigQueryForm;
import net.lab1024.sa.draw.poolconfig.domain.form.PrizePoolConfigUpdateForm;
import net.lab1024.sa.draw.poolconfig.domain.vo.PrizePoolConfigVO;
import net.lab1024.sa.draw.poolconfig.service.PrizePoolConfigService;
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
 * 奖池配置 Controller
 *
 * @Author weolwo
 * @Date 2026-04-19 09:42:12
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "奖池配置")
@RequestMapping("/prizePoolConfig")
public class PrizePoolConfigController {

    private final PrizePoolConfigService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission(":query")
    public ResponseDTO<PageResult<PrizePoolConfigVO>> queryPage(@RequestBody @Valid PrizePoolConfigQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission(":addProposal")
    public ResponseDTO<String> add(@RequestBody @Valid PrizePoolConfigAddForm addForm) {
        return Service.add(addForm);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission(":update")
    public ResponseDTO<String> update(@RequestBody @Valid PrizePoolConfigUpdateForm updateForm) {
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
