package sa.mall.favorite.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sa.base.common.domain.ResponseDTO;
import sa.mall.favorite.domain.vo.MallFavoriteStatVO;
import sa.mall.favorite.service.MallFavoriteService;

/**
 * 商城-商品收藏 Controller
 *
 * <p><b>只有一个统计接口</b>。收藏是 C 端用户的行为，后台既不该增删，
 * 也不需要看逐条明细 ——「张三收藏了 T 恤」这条记录本身回答不了任何运营问题。
 * 生成器留的分页查询与增删改已全部删除。
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

    private final MallFavoriteService mallFavoriteService;

    @Operation(summary = "收藏统计与排行 @author weolwo")
    @GetMapping("/queryStat")
    @SaCheckPermission("mallFavorite:query")
    public ResponseDTO<MallFavoriteStatVO> queryStat(@RequestParam(required = false) Integer rankTopN) {
        return mallFavoriteService.queryStat(rankTopN);
    }
}
