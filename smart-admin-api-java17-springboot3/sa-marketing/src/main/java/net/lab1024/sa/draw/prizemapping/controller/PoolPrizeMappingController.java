package net.lab1024.sa.draw.prizemapping.controller;

import net.lab1024.sa.base.common.domain.ValidateList;
import net.lab1024.sa.draw.prizemapping.domain.entity.PoolPrizeMapping;
import net.lab1024.sa.draw.prizemapping.domain.form.PoolPrizeMappingAddForm;
import net.lab1024.sa.draw.prizemapping.domain.form.PoolPrizeMappingQueryForm;
import net.lab1024.sa.draw.prizemapping.domain.form.PoolPrizeMappingUpdateForm;
import net.lab1024.sa.draw.prizemapping.domain.vo.PoolPrizeMappingVO;
import net.lab1024.sa.draw.prizemapping.service.PoolPrizeMappingService;
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
 * 奖池奖项映射 Controller
 *
 * @Author weolwo
 * @Date 2026-04-19 10:07:03
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "奖池奖项映射")
@RequestMapping("/poolPrizeMapping")
public class PoolPrizeMappingController {

    private final PoolPrizeMappingService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission(":query")
    public ResponseDTO<PageResult<PoolPrizeMappingVO>> queryPage(@RequestBody @Valid PoolPrizeMappingQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission(":addProposal")
    public ResponseDTO<String> add(@RequestBody @Valid PoolPrizeMappingAddForm addForm) {
        return Service.add(addForm);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission(":update")
    public ResponseDTO<String> update(@RequestBody @Valid PoolPrizeMappingUpdateForm updateForm) {
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
