package sa.mall.exchangelimit.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import sa.base.common.domain.ValidateList;
import sa.mall.exchangelimit.domain.entity.MallExchangeLimit;
import sa.mall.exchangelimit.domain.form.MallExchangeLimitAddForm;
import sa.mall.exchangelimit.domain.form.MallExchangeLimitQueryForm;
import sa.mall.exchangelimit.domain.form.MallExchangeLimitUpdateForm;
import sa.mall.exchangelimit.domain.vo.MallExchangeLimitVO;
import sa.mall.exchangelimit.service.MallExchangeLimitService;
import sa.base.common.domain.ResponseDTO;
import sa.base.common.domain.PageResult;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
/**
 * 商城-会员限兑计数 Controller
 *
 * @Author weolwo
 * @Date 2026-08-22 19:33:25
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "商城-会员限兑计数")
@RequestMapping("/mallExchangeLimit")
public class MallExchangeLimitController {

    private final MallExchangeLimitService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission("mallExchangeLimit:query")
    public ResponseDTO<PageResult<MallExchangeLimitVO>> queryPage(@RequestBody @Valid MallExchangeLimitQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission("mallExchangeLimit:add")
    public ResponseDTO<String> add(@RequestBody @Valid MallExchangeLimitAddForm addForm) {
        return Service.add(addForm);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission("mallExchangeLimit:update")
    public ResponseDTO<String> update(@RequestBody @Valid MallExchangeLimitUpdateForm updateForm) {
        return Service.update(updateForm);
    }

    @Operation(summary = "批量删除")
    @PostMapping("/batchDelete")
    @SaCheckPermission("mallExchangeLimit:delete")
    public ResponseDTO<String> batchDelete(@RequestBody ValidateList<Long> idList) {
        return Service.batchDelete(idList);
    }

    @Operation(summary = "单个删除")
    @GetMapping("/delete/{id}")
    @SaCheckPermission("mallExchangeLimit:delete")
    public ResponseDTO<String> batchDelete(@PathVariable Long id) {
        return Service.delete(id);
    }
}
