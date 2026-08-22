package sa.mall.favorite.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sa.base.common.domain.PageResult;
import sa.base.common.domain.ResponseDTO;
import sa.mall.favorite.domain.form.MallFavoriteQueryForm;
import sa.mall.favorite.domain.vo.MallFavoriteVO;
import sa.mall.favorite.service.MallFavoriteService;

/**
 * 商城-商品收藏 Controller
 *
 * @Author weolwo
 * @Date 2026-08-22 19:34:44
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "商城-商品收藏")
@RequestMapping("/mallFavorite")
public class MallFavoriteController {

    private final MallFavoriteService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission(":query")
    public ResponseDTO<PageResult<MallFavoriteVO>> queryPage(@RequestBody @Valid MallFavoriteQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }


}
