package net.lab1024.sa.stat.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.stat.domain.vo.ParticipationVO;
import net.lab1024.sa.stat.service.MarketingStatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 营销统计 Controller
 *
 * <p>前缀与权限串遵循 docs/营销中台-数据统计方案.md §4 的约定：
 * 统一前缀 /marketingStat，权限串写全（不要出现缺前缀的裸串）。
 *
 * @Author weolwo
 * @Date 2026-08-02
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "营销统计")
@RequestMapping("/marketingStat")
public class MarketingStatController {

    private final MarketingStatService marketingStatService;

    @Operation(summary = "首页参与统计：趋势 + 各玩法类型参与人数")
    @GetMapping("/participation")
    @SaCheckPermission("marketingStat:participation")
    public ResponseDTO<ParticipationVO> participation(@RequestParam(required = false) Integer days) {
        return ResponseDTO.ok(marketingStatService.participation(days));
    }
}
