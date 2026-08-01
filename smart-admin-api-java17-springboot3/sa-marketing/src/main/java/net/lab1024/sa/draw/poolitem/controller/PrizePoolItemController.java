package net.lab1024.sa.draw.poolitem.controller;

import net.lab1024.sa.base.common.domain.ValidateList;
import net.lab1024.sa.draw.poolitem.domain.entity.PrizePoolItem;
import net.lab1024.sa.draw.poolitem.domain.form.PrizePoolItemAddForm;
import net.lab1024.sa.draw.poolitem.domain.form.PrizePoolItemQueryForm;
import net.lab1024.sa.draw.poolitem.domain.form.PrizePoolItemUpdateForm;
import net.lab1024.sa.draw.poolitem.domain.vo.PrizePoolItemVO;
import net.lab1024.sa.draw.poolitem.service.PrizePoolItemService;
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
 * 奖池奖项库 Controller
 *
 * @Author weolwo
 * @Date 2026-04-19 09:52:45
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "奖池奖项库")
@RequestMapping("/prizePoolItem")
public class PrizePoolItemController {

    private final PrizePoolItemService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission("prizePoolItem:query")
    public ResponseDTO<PageResult<PrizePoolItemVO>> queryPage(@RequestBody @Valid PrizePoolItemQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission("prizePoolItem:add")
    public ResponseDTO<String> add(@RequestBody @Valid PrizePoolItemAddForm addForm) {
        return Service.add(addForm);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission("prizePoolItem:update")
    public ResponseDTO<String> update(@RequestBody @Valid PrizePoolItemUpdateForm updateForm) {
        return Service.update(updateForm);
    }

    @Operation(summary = "批量删除")
    @PostMapping("/batchDelete")
    @SaCheckPermission("prizePoolItem:delete")
    public ResponseDTO<String> batchDelete(@RequestBody ValidateList<Long> idList) {
        return Service.batchDelete(idList);
    }

    @Operation(summary = "单个删除")
    @GetMapping("/delete/{id}")
    @SaCheckPermission("prizePoolItem:delete")
    public ResponseDTO<String> batchDelete(@PathVariable Long id) {
        return Service.delete(id);
    }
}
