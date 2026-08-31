package solvela.admin.module.scriptengine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.activity.ActivityConfig;
import solvela.activity.manager.ActivityConfigManager;
import solvela.admin.module.scriptengine.domain.vo.ScriptRefCandidateVO;
import solvela.draw.DrawConfig;
import solvela.draw.PrizePoolConfig;
import solvela.draw.drawconfig.manager.DrawConfigManager;
import solvela.draw.poolconfig.manager.PrizePoolConfigManager;
import solvela.scriptengine.spi.ScriptRefPoint;
import solvela.task.TaskTemplate;
import solvela.task.tasktemplate.manager.TaskTemplateManager;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 挂载时「业务对象编码」的候选清单。
 *
 * <h3>为什么不让前端直接调各业务模块的 optionList</h3>
 * 那样挂一个脚本就要求操作人同时具备 {@code activityConfig:query}、{@code taskTemplate:query} 等一串权限 ——
 * 而他要做的事只是「把脚本挂上去」。权限该跟着<b>动作</b>走，不该跟着数据来源走。
 *
 * <p>另一个原因是形状：四种对象的 VO 各不相同，前端得写四份渲染。
 * 在服务端收敛成 {@code code + name + remark} 一种形状，前端只认挂载点。
 *
 * <p>🔴 <b>不做「已被占用就过滤掉」</b>：同一个业务对象换挂另一个脚本是正常操作
 * （重复挂等于替换，见 {@code ScriptRefService#bind}）。把已占用的藏起来，
 * 运营会以为这个活动消失了。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptRefCandidateService {

    private final ActivityConfigManager activityConfigManager;

    private final DrawConfigManager drawConfigManager;

    private final TaskTemplateManager taskTemplateManager;

    private final PrizePoolConfigManager prizePoolConfigManager;

    /**
     * 某个挂载点能挂到哪些业务对象上。
     *
     * <p>未识别的类型返回空列表而不是抛异常 —— 新增挂载点时忘了在这里补一条，
     * 表现应该是「下拉是空的」这种一眼能看见的样子，而不是整个挂载表单打不开。
     */
    public List<ScriptRefCandidateVO> list(ScriptRefPoint point) {
        return switch (point.getRefType()) {
            case "ACTIVITY" -> activities();
            case "DRAW" -> draws();
            case "TASK_TEMPLATE" -> taskTemplates();
            case "PRIZE_POOL" -> pools();
            default -> {
                log.warn("【脚本挂载】挂载点 {} 的候选清单没有实现，下拉将是空的", point.name());
                yield List.of();
            }
        };
    }

    /**
     * 活动：<b>不过滤状态</b>。已下线的活动照样要能挂脚本 ——
     * 运营常常是先把活动下线、改完配置再上线，此时下拉里找不到它就卡住了。
     */
    private List<ScriptRefCandidateVO> activities() {
        return activityConfigManager.lambdaQuery()
                .orderByDesc(ActivityConfig::getId).list().stream()
                .map(a -> new ScriptRefCandidateVO(a.getActivityCode(), a.getActivityName(),
                        a.getActivityType()))
                .toList();
    }

    private List<ScriptRefCandidateVO> draws() {
        Map<String, String> activityNames = activityNameMap();
        return drawConfigManager.lambdaQuery()
                .orderByDesc(DrawConfig::getId).list().stream()
                .map(d -> new ScriptRefCandidateVO(d.getDrawCode(), d.getDrawName(),
                        // 抽奖名默认就是「活动名-抽奖」，光看名字分不出是哪个活动的，带上活动编码
                        activityNames.getOrDefault(d.getActivityCode(), d.getActivityCode())))
                .toList();
    }

    private List<ScriptRefCandidateVO> taskTemplates() {
        return taskTemplateManager.lambdaQuery()
                .orderByDesc(TaskTemplate::getId).list().stream()
                .map(t -> new ScriptRefCandidateVO(t.getTemplateCode(), t.getTemplateName(), t.getTaskType()))
                .toList();
    }

    private List<ScriptRefCandidateVO> pools() {
        Map<String, String> activityNames = activityNameMap();
        return prizePoolConfigManager.lambdaQuery()
                .orderByDesc(PrizePoolConfig::getId).list().stream()
                .map(p -> new ScriptRefCandidateVO(p.getPoolCode(), p.getPoolName(),
                        activityNames.getOrDefault(p.getActivityCode(), p.getActivityCode())))
                .toList();
    }

    private Map<String, String> activityNameMap() {
        return activityConfigManager.list().stream()
                .collect(Collectors.toMap(ActivityConfig::getActivityCode, ActivityConfig::getActivityName,
                        (a, b) -> a));
    }
}
