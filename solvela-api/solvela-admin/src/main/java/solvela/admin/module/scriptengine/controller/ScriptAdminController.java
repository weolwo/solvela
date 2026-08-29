package solvela.admin.module.scriptengine.controller;

import solvela.web.RequiresPermission;
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
import solvela.admin.module.system.login.domain.RequestEmployee;
import solvela.exception.BusinessException;
import solvela.admin.auth.CurrentEmployee;
import solvela.base.constant.SwaggerTagConst;
import solvela.admin.module.scriptengine.domain.form.ScriptBindForm;
import solvela.admin.module.scriptengine.domain.vo.ScriptRefPointVO;
import solvela.scriptengine.domain.dto.ScriptRefDTO;
import solvela.scriptengine.domain.dto.ScriptDTO;
import solvela.scriptengine.service.ScriptQueryService;
import solvela.scriptengine.service.ScriptRefService;
import solvela.scriptengine.spi.ScriptRefPoint;

import java.util.Arrays;
import java.util.List;

/**
 * 脚本管理接口。
 *
 * <p>🔴 <b>没有新增/编辑/删除脚本的接口，这是设计而不是遗漏</b>：
 * 脚本内容的权威在 {@code resources/scripts/} 下的文件，改脚本必须走 git + 发版。
 * 这里只提供「看」和「挂在哪」。
 *
 * <p>权限分两档：读用 {@code script:query}，<b>挂载/摘除单列 {@code script:bind}</b>。
 * 分开是因为这两件事的后果完全不同 —— 挂一个脚本等于改变某个奖池/活动的线上判定逻辑，
 * 和「看一眼有哪些脚本」不该是同一个权限。
 */
@RestController
@Tag(name = SwaggerTagConst.Support.SCRIPT_DOC)
@RequiredArgsConstructor
@RequestMapping("/script")
public class ScriptAdminController {

    private final ScriptQueryService scriptQueryService;

    private final ScriptRefService scriptRefService;

    @Operation(summary = "【用户】脚本-列表（只读，权威在项目文件里）")
    @RequiresPermission("script:query")
    @GetMapping("/list")
    public List<ScriptDTO> list() {
        return scriptQueryService.listAll();
    }

    @Operation(summary = "【用户】脚本-详情，含内容与引用它的业务对象")
    @RequiresPermission("script:query")
    @GetMapping("/detail")
    public ScriptDTO detail(@RequestParam String scriptCode) {
        return scriptQueryService.detail(scriptCode);
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
            vo.setExpectedScene(point.getExpectedScene().name());
            vo.setExpectedSceneTitle(point.getExpectedScene().getTitle());
            return vo;
        }).toList();
    }

    @Operation(summary = "【用户】脚本-挂载到业务对象")
    @RequiresPermission("script:bind")
    @PostMapping("/ref/bind")
    public void bind(@RequestBody @Valid ScriptBindForm form) {
        RequestEmployee user = CurrentEmployee.orNull();
        scriptRefService.bind(toPoint(form.getRefPoint()), form.getRefId(), form.getScriptCode(),
                user == null ? null : user.getUserName());
    }

    @Operation(summary = "【用户】脚本-摘除挂载")
    @RequiresPermission("script:bind")
    @PostMapping("/ref/unbind/{refPoint}/{refId}")
    public void unbind(@PathVariable String refPoint, @PathVariable String refId) {
        scriptRefService.unbind(toPoint(refPoint), refId);
    }

    private ScriptRefPoint toPoint(String refPoint) {
        return ScriptRefPoint.of(refPoint).orElseThrow(() ->
                new BusinessException("挂载点 [" + refPoint + "] 不存在，合法值见 /script/ref/point/list"));
    }
}
