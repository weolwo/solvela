package solvela.admin.module.draw.drawconfig.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.admin.auth.CurrentEmployee;
import solvela.admin.module.draw.drawconfig.domain.form.DrawConfigForm;
import solvela.admin.module.draw.drawconfig.domain.vo.DrawConfigVO;
import solvela.admin.module.system.login.domain.RequestEmployee;
import solvela.base.util.SolvelaBeanUtil;
import solvela.draw.DrawConfig;
import solvela.draw.drawconfig.service.DrawConfigService;
import solvela.web.RequiresPermission;

import java.util.List;

/**
 * 抽奖配置 Controller。
 *
 * <h3>它补的是哪一层</h3>
 * 抽奖原本只有「活动 → N 个奖池」两层，玩法级参数（重置周期、抽奖算法）只能挂到奖池上，
 * 而脚本更是没地方挂 —— 脚本引擎要一个业务对象编码，「一次抽奖」当时不是一个对象。
 * 这一层与 {@code t_lottery_config} 同级。
 *
 * <p>🔴 <b>一个活动一套抽奖</b>，由唯一键 {@code uk_draw_activity} 保证。
 * 服务端会先查一次给出人话报错，但真正兜底的是那个索引。
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "抽奖配置")
@RequestMapping("/drawConfig")
public class DrawConfigController {

    private final DrawConfigService drawConfigService;

    @Operation(summary = "【用户】抽奖配置-列表")
    @RequiresPermission("drawConfig:query")
    @GetMapping("/list")
    public List<DrawConfigVO> list() {
        return drawConfigService.listWithStats().stream()
                .map(dto -> SolvelaBeanUtil.copy(dto, DrawConfigVO.class))
                .toList();
    }

    @Operation(summary = "【用户】抽奖配置-按活动查。活动没有抽奖配置时返回 null")
    @RequiresPermission("drawConfig:query")
    @GetMapping("/detail/byActivity/{activityCode}")
    public DrawConfigVO detailByActivity(@PathVariable String activityCode) {
        DrawConfig config = drawConfigService.getByActivityCode(activityCode);
        return config == null ? null : SolvelaBeanUtil.copy(config, DrawConfigVO.class);
    }

    @Operation(summary = "【用户】抽奖配置-生成一个没被占用的抽奖编码")
    @RequiresPermission("drawConfig:add")
    @GetMapping("/generateCode")
    public String generateCode() {
        return drawConfigService.generateDrawCode();
    }

    @Operation(summary = "【用户】抽奖配置-新增")
    @RequiresPermission("drawConfig:add")
    @PostMapping("/add")
    public Long add(@RequestBody @Valid DrawConfigForm form) {
        return drawConfigService.add(SolvelaBeanUtil.copy(form, DrawConfig.class), operator());
    }

    @Operation(summary = "【用户】抽奖配置-修改。抽奖编码与活动编码不可改")
    @RequiresPermission("drawConfig:update")
    @PostMapping("/update")
    public void update(@RequestBody @Valid DrawConfigForm form) {
        drawConfigService.update(SolvelaBeanUtil.copy(form, DrawConfig.class), operator());
    }

    @Operation(summary = "【用户】抽奖配置-删除。底下还有奖池时会被拒绝")
    @RequiresPermission("drawConfig:delete")
    @GetMapping("/delete/{id}")
    public void delete(@PathVariable Long id) {
        drawConfigService.delete(id);
    }

    private String operator() {
        RequestEmployee user = CurrentEmployee.orNull();
        return user == null ? null : user.getUserName();
    }
}
