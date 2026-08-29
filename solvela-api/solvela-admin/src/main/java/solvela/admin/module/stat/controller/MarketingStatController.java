package solvela.admin.module.stat.controller;

import solvela.web.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import solvela.stat.domain.dto.EventHealthDTO;
import solvela.stat.domain.dto.GameplayDTO;
import solvela.stat.domain.dto.OverviewDTO;
import solvela.stat.domain.dto.ParticipationDTO;
import solvela.stat.domain.dto.PrizeHealthDTO;
import solvela.stat.domain.dto.TaskFunnelDTO;
import solvela.stat.domain.dto.TopMemberDTO;
import solvela.stat.service.MarketingStatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 营销统计 Controller
 *
 * <p>前缀与权限串遵循 docs/营销中台-数据统计方案.md §4 的约定：
 * 统一前缀 /marketingStat，权限串写全（不要出现缺前缀的裸串）。
 *
 * <p>🔴 <b>全部接口共用一个权限串 {@code marketingStat:query}</b>：统计接口都是只读的，
 * 拆成六个功能点只会让运营在授权界面上勾六次而没有任何多出来的控制力。
 * 对应的功能点见 {@code v3.52.0.sql} —— <b>库里没有功能点的权限串等于「除超管外谁也用不了」</b>，
 * 而界面上完全看不出线索，本项目已因此栽过两次（见交接文档 §4.6④）。
 *
 * <p>冷热分开是刻意的（方案 §4）：大屏轮询时 overview / prizeHealth / gameplay 走 30 秒，
 * taskFunnel / eventHealth / topMembers 走 5 分钟，避免为了刷一个每天只变几次的漏斗
 * 把整套接口都拉高频。节流在前端做，后端不限制。
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
    @RequiresPermission("marketingStat:query")
    public ParticipationDTO participation(@RequestParam(required = false) Integer days) {
        return marketingStatService.participation(days);
    }

    @Operation(summary = "大屏总览：活动状态分布（启用/禁用两态）+ 活动卡片 + 逾期未开奖告警")
    @GetMapping("/overview")
    @RequiresPermission("marketingStat:query")
    public OverviewDTO overview() {
        return marketingStatService.overview();
    }

    @Operation(summary = "发奖健康度：条数/价值双口径 + 待审积压 + 失败TOP + 价值趋势")
    @GetMapping("/prizeHealth")
    @RequiresPermission("marketingStat:query")
    public PrizeHealthDTO prizeHealth(@RequestParam(required = false) String activityCode,
                                                  @RequestParam(required = false) Integer days) {
        return marketingStatService.prizeHealth(activityCode, days);
    }

    @Operation(summary = "玩法运行态：按活动类型返回抽奖状态分布 / 彩票期号 / 任务列表")
    @GetMapping("/gameplay/{activityCode}")
    @RequiresPermission("marketingStat:query")
    public GameplayDTO gameplay(@PathVariable String activityCode) {
        return marketingStatService.gameplay(activityCode);
    }

    @Operation(summary = "任务阶梯流失漏斗")
    @GetMapping("/taskFunnel/{taskConfigId}")
    @RequiresPermission("marketingStat:query")
    public TaskFunnelDTO taskFunnel(@PathVariable Long taskConfigId) {
        return marketingStatService.taskFunnel(taskConfigId);
    }

    @Operation(summary = "事件健康度：推进/丢弃趋势，丢弃按 discard_code 聚类")
    @GetMapping("/eventHealth")
    @RequiresPermission("marketingStat:query")
    public EventHealthDTO eventHealth(@RequestParam(required = false) Integer days) {
        return marketingStatService.eventHealth(days);
    }

    @Operation(summary = "Top 获奖用户（营销域口径：已发出价值）")
    @GetMapping("/topMembers")
    @RequiresPermission("marketingStat:query")
    public List<TopMemberDTO> topMembers(@RequestParam(required = false) Integer days) {
        return marketingStatService.topMembers(days);
    }
}
