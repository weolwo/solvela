package solvela.admin.module.mall.sku.controller;

import solvela.web.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.domain.PageResult;
import solvela.base.util.SolvelaBeanUtil;
import solvela.admin.module.mall.sku.domain.form.MallSkuQueryForm;
import solvela.mall.sku.domain.query.MallSkuQuery;
import solvela.admin.module.mall.sku.domain.vo.MallSkuVO;
import solvela.mall.sku.domain.dto.MallSkuDTO;
import solvela.mall.sku.service.MallSkuService;

/**
 * 商城-库存总览 Controller
 *
 * <p><b>只读</b>。这个页面回答的是「哪些规格快卖光了」——
 * 一个在别处答不了的问题：商品列表只有商品级的库存合计（一个规格断货看不出来），
 * 商品编辑页一次只能看一个商品。
 *
 * <p>改库存在<b>商品编辑页</b>，不在这里：那边有批量设置，而且库存改动会连同价格、
 * 状态一起走同一个聚合保存事务。两个入口写同一批数据，迟早对不上。
 * 生成器留的 add / update 已删除。
 *
 * @Author weolwo
 * @Date 2026-08-22 19:37:50
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "商城-库存总览")
@RequestMapping("/mallSku")
public class MallSkuController {

    private final MallSkuService mallSkuService;

    @Operation(summary = "库存总览分页查询（按可用库存升序） @author weolwo")
    @PostMapping("/queryPage")
    @RequiresPermission("mallSku:query")
    public PageResult<MallSkuVO> queryPage(@RequestBody @Valid MallSkuQueryForm queryForm) {
        PageResult<MallSkuDTO> page = mallSkuService.queryPage(SolvelaBeanUtil.copy(queryForm, MallSkuQuery.class));
        return SolvelaPageUtil.convert2PageResult(page, MallSkuVO.class);
    }
}
