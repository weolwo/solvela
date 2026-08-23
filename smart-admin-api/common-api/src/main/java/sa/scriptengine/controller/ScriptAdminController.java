package sa.scriptengine.controller;

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
import sa.base.common.domain.RequestUser;
import sa.base.common.domain.ResponseDTO;
import sa.base.common.exception.BusinessException;
import sa.base.common.util.SmartRequestUtil;
import sa.base.constant.SwaggerTagConst;
import sa.scriptengine.domain.form.ScriptBindForm;
import sa.scriptengine.domain.vo.ScriptRefPointVO;
import sa.scriptengine.domain.vo.ScriptRefVO;
import sa.scriptengine.domain.vo.ScriptVO;
import sa.scriptengine.service.ScriptQueryService;
import sa.scriptengine.service.ScriptRefService;
import sa.scriptengine.spi.ScriptRefPoint;

import java.util.Arrays;
import java.util.List;

/**
 * 脚本管理接口。
 *
 * <p>🔴 <b>没有新增/编辑/删除脚本的接口，这是设计而不是遗漏</b>：
 * 脚本内容的权威在 {@code resources/scripts/} 下的文件，改脚本必须走 git + 发版。
 * 这里只提供「看」和「挂在哪」。
 */
@RestController
@Tag(name = SwaggerTagConst.Support.SCRIPT_DOC)
@RequiredArgsConstructor
@RequestMapping("/script")
public class ScriptAdminController {

    private final ScriptQueryService scriptQueryService;

    private final ScriptRefService scriptRefService;

    @Operation(summary = "【用户】脚本-列表（只读，权威在项目文件里）")
    @GetMapping("/list")
    public ResponseDTO<List<ScriptVO>> list() {
        return ResponseDTO.ok(scriptQueryService.listAll());
    }

    @Operation(summary = "【用户】脚本-详情，含内容与引用它的业务对象")
    @GetMapping("/detail")
    public ResponseDTO<ScriptVO> detail(@RequestParam String scriptCode) {
        return ResponseDTO.ok(scriptQueryService.detail(scriptCode));
    }

    @Operation(summary = "【用户】脚本-改这个脚本会影响哪些业务对象")
    @GetMapping("/refs")
    public ResponseDTO<List<ScriptRefVO>> refs(@RequestParam String scriptCode) {
        return ResponseDTO.ok(scriptRefService.findRefsOfScript(scriptCode));
    }

    @Operation(summary = "【用户】脚本-某个业务对象身上挂了哪些脚本")
    @GetMapping("/refs/owner")
    public ResponseDTO<List<ScriptRefVO>> refsOfOwner(@RequestParam String refType, @RequestParam String refId) {
        return ResponseDTO.ok(scriptRefService.findRefsOfOwner(refType, refId));
    }

    @Operation(summary = "【用户】脚本-可挂载点清单，前端下拉用")
    @GetMapping("/ref/point/list")
    public ResponseDTO<List<ScriptRefPointVO>> refPoints() {
        return ResponseDTO.ok(Arrays.stream(ScriptRefPoint.values()).map(point -> {
            ScriptRefPointVO vo = new ScriptRefPointVO();
            vo.setRefPoint(point.name());
            vo.setTitle(point.getTitle());
            vo.setRefType(point.getRefType());
            vo.setRefSlot(point.getRefSlot());
            vo.setExpectedScene(point.getExpectedScene().name());
            vo.setExpectedSceneTitle(point.getExpectedScene().getTitle());
            return vo;
        }).toList());
    }

    @Operation(summary = "【用户】脚本-挂载到业务对象")
    @PostMapping("/ref/bind")
    public ResponseDTO<String> bind(@RequestBody @Valid ScriptBindForm form) {
        RequestUser user = SmartRequestUtil.getRequestUser();
        scriptRefService.bind(toPoint(form.getRefPoint()), form.getRefId(), form.getScriptCode(),
                user == null ? null : user.getUserName());
        return ResponseDTO.ok();
    }

    @Operation(summary = "【用户】脚本-摘除挂载")
    @PostMapping("/ref/unbind/{refPoint}/{refId}")
    public ResponseDTO<String> unbind(@PathVariable String refPoint, @PathVariable String refId) {
        scriptRefService.unbind(toPoint(refPoint), refId);
        return ResponseDTO.ok();
    }

    private ScriptRefPoint toPoint(String refPoint) {
        return ScriptRefPoint.of(refPoint).orElseThrow(() ->
                new BusinessException("挂载点 [" + refPoint + "] 不存在，合法值见 /script/ref/point/list"));
    }
}
