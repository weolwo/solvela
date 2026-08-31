package solvela.admin.module.scriptengine.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import solvela.admin.auth.CurrentEmployee;
import solvela.admin.module.scriptengine.domain.form.ScriptBindForm;
import solvela.admin.module.scriptengine.domain.form.ScriptSaveForm;
import solvela.admin.module.scriptengine.domain.vo.ScriptRefCandidateVO;
import solvela.admin.module.scriptengine.domain.vo.ScriptRefPointVO;
import solvela.admin.module.scriptengine.service.ScriptRefCandidateService;
import solvela.admin.module.system.login.domain.RequestEmployee;
import solvela.base.constant.SwaggerTagConst;
import solvela.exception.BusinessException;
import solvela.scriptengine.domain.ScriptSaveCommand;
import solvela.scriptengine.domain.dto.ScriptDTO;
import solvela.scriptengine.domain.dto.ScriptRefDTO;
import solvela.scriptengine.service.ScriptEditService;
import solvela.scriptengine.service.ScriptQueryService;
import solvela.scriptengine.service.ScriptRefService;
import solvela.scriptengine.spi.ScriptRefPoint;
import solvela.web.RequiresPermission;

import java.util.Arrays;
import java.util.List;

/**
 * 脚本管理接口。
 *
 * <h3>脚本内容现在可以在这里写</h3>
 * 以前这个类只提供「看」和「挂在哪」，因为脚本权威在 {@code resources/scripts/}，
 * 改脚本必须走 git + 发版。那条路走不通 —— 一个活动动辄要好几个脚本，
 * 每改一行都发一次版，运营节奏根本等不起。
 *
 * <p>现在权威是 {@code t_script}，文件退回开发工作区的位置。作为交换，
 * 这里必须自己把发版流程里那几道闸补回来：
 * <ul>
 *   <li><b>保存 ≠ 生效</b>：{@link #save} 只加一个版本，{@link #activate} 才改变线上行为；</li>
 *   <li><b>坏脚本进不了库</b>：保存前做语法校验，不通过直接拒绝；</li>
 *   <li><b>回滚 = 激活旧版本</b>，和发布是同一个动作，因此不存在「回滚没测过」这回事。</li>
 * </ul>
 *
 * <h3>权限分四档，按后果分</h3>
 * <ul>
 *   <li>{@code script:query} —— 看；</li>
 *   <li>{@code script:edit} —— 写一个新版本（不影响线上）；</li>
 *   <li>{@code script:publish} —— <b>激活，改变线上逻辑</b>；</li>
 *   <li>{@code script:bind} —— <b>挂载，改变某个活动/奖池走哪段逻辑</b>。</li>
 * </ul>
 * 后两个才是真正能改线上行为的动作，和「存个草稿」不该共用一个权限。
 */
@RestController
@Tag(name = SwaggerTagConst.Support.SCRIPT_DOC)
@RequiredArgsConstructor
@RequestMapping("/script")
public class ScriptAdminController {

    private final ScriptQueryService scriptQueryService;

    private final ScriptEditService scriptEditService;

    private final ScriptRefService scriptRefService;

    private final ScriptRefCandidateService scriptRefCandidateService;

    // ------------------------------------------------------------------
    // 查
    // ------------------------------------------------------------------

    @Operation(summary = "【用户】脚本-列表，每个脚本一行，取当前激活版本")
    @RequiresPermission("script:query")
    @GetMapping("/list")
    public List<ScriptDTO> list() {
        return scriptQueryService.listActive();
    }

    @Operation(summary = "【用户】脚本-某个脚本的全部版本，新的在前，含内容")
    @RequiresPermission("script:query")
    @GetMapping("/versions")
    public List<ScriptDTO> versions(@RequestParam String scriptCode) {
        return scriptQueryService.listVersions(scriptCode);
    }

    @Operation(summary = "【用户】脚本-单个版本详情，含内容")
    @RequiresPermission("script:query")
    @GetMapping("/detail")
    public ScriptDTO detail(@RequestParam Long id) {
        return scriptQueryService.detail(id);
    }

    @Operation(summary = "【用户】脚本-改这个脚本会影响哪些业务对象")
    @RequiresPermission("script:query")
    @GetMapping("/refs")
    public List<ScriptRefDTO> refs(@RequestParam String scriptCode) {
        return scriptRefService.findRefsOfScript(scriptCode);
    }

    @Operation(summary = "【用户】脚本-某个业务对象身上挂了哪些脚本")
    @RequiresPermission("script:query")
    @GetMapping("/refs/owner")
    public List<ScriptRefDTO> refsOfOwner(@RequestParam String refType, @RequestParam String refId) {
        return scriptRefService.findRefsOfOwner(refType, refId);
    }

    @Operation(summary = "【用户】脚本-可挂载点清单，前端下拉用")
    @RequiresPermission("script:query")
    @GetMapping("/ref/point/list")
    public List<ScriptRefPointVO> refPoints() {
        return Arrays.stream(ScriptRefPoint.values()).map(point -> {
            ScriptRefPointVO vo = new ScriptRefPointVO();
            vo.setRefPoint(point.name());
            vo.setTitle(point.getTitle());
            vo.setRefType(point.getRefType());
            vo.setRefSlot(point.getRefSlot());
            vo.setOwnerTitle(point.getOwnerTitle());
            vo.setKeyed(point.isKeyed());
            vo.setWired(point.isWired());
            vo.setKeyTitle(point.getKeyTitle());
            vo.setExpectedScene(point.getExpectedScene().name());
            vo.setExpectedSceneTitle(point.getExpectedScene().getTitle());
            return vo;
        }).toList();
    }

    @Operation(summary = "【用户】脚本-某挂载点可选的业务对象，挂载表单的下拉用")
    @RequiresPermission("script:query")
    @GetMapping("/ref/candidate/list")
    public List<ScriptRefCandidateVO> refCandidates(@RequestParam String refPoint) {
        return scriptRefCandidateService.list(toPoint(refPoint));
    }

    // ------------------------------------------------------------------
    // 写
    // ------------------------------------------------------------------

    @Operation(summary = "【用户】脚本-生成一个没被占用的脚本编码（10位大写字母数字，首位 Q）")
    @RequiresPermission("script:edit")
    @GetMapping("/generateCode")
    public String generateCode() {
        return scriptEditService.generateScriptCode();
    }

    @Operation(summary = "【用户】脚本-保存一个新版本（语法不通过则拒绝；保存不等于生效）")
    @RequiresPermission("script:edit")
    @PostMapping("/save")
    public Long save(@RequestBody @Valid ScriptSaveForm form) {
        return scriptEditService.save(new ScriptSaveCommand(
                form.getScriptCode(), form.getScriptName(), form.getScene(),
                form.getDescription(), form.getContent(), form.getChangeLog(), operator()));
    }

    @Operation(summary = "【用户】脚本-激活某个版本。发布与回滚都走这个接口")
    @RequiresPermission("script:publish")
    @PostMapping("/activate/{id}")
    public void activate(@PathVariable Long id) {
        scriptEditService.activate(id, operator());
    }

    @Operation(summary = "【用户】脚本-挂载到业务对象")
    @RequiresPermission("script:bind")
    @PostMapping("/ref/bind")
    public void bind(@RequestBody @Valid ScriptBindForm form) {
        scriptRefService.bind(toPoint(form.getRefPoint()), form.getRefId(), form.getRefKey(),
                form.getScriptCode(), operator());
    }

    @Operation(summary = "【用户】脚本-摘除挂载")
    @RequiresPermission("script:bind")
    @PostMapping("/ref/unbind/{refPoint}/{refId}")
    public void unbind(@PathVariable String refPoint, @PathVariable String refId,
                       @RequestParam(required = false) String refKey) {
        scriptRefService.unbind(toPoint(refPoint), refId, refKey);
    }

    private ScriptRefPoint toPoint(String refPoint) {
        return ScriptRefPoint.of(refPoint).orElseThrow(() ->
                new BusinessException("挂载点 [" + refPoint + "] 不存在，合法值见 /script/ref/point/list"));
    }

    private String operator() {
        RequestEmployee user = CurrentEmployee.orNull();
        return user == null ? null : user.getUserName();
    }
}
