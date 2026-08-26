package solvela.admin.module.scriptengine.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.base.domain.ResponseDTO;
import solvela.base.exception.EngineScriptException;
import solvela.base.constant.SwaggerTagConst;
import solvela.admin.module.scriptengine.controller.form.ScriptTestForm;
import solvela.scriptengine.core.EngineFunctionRegistry;
import solvela.scriptengine.domain.ExecutableScript;
import solvela.scriptengine.domain.ScriptCheckResultVO;
import solvela.scriptengine.domain.ScriptSceneDocDTO;
import solvela.scriptengine.spi.EngineContext;
import solvela.scriptengine.spi.ScriptEngine;
import solvela.scriptengine.spi.ScriptScene;

import java.util.Arrays;
import java.util.List;

/**
 * 脚本引擎的文档、校验与在线试跑。
 *
 * <p>🔴 <b>{@code /online/test} 单列 {@code script:test} 权限，且它的风险会随时间上升：</b>
 * 这个接口执行调用方传来的任意脚本，而脚本能调用<b>所有已注册的 {@code @ScriptFunction}</b>。
 * 目前只有 {@code tool_} 域的纯函数，跑一万次也不改任何数据；
 * 但等 {@code ledger_} / {@code mall_} 这类会动账、会扣库存的函数注册进来，
 * 这个接口就等于「拿着任意参数直接调用业务写操作」。
 *
 * <p>那时候光靠权限是不够的，正确的做法是给 {@code @ScriptFunction} 加一个
 * 「是否有副作用」的标记，在线试跑只允许调用无副作用的那一批。见白皮书 §7.1。
 */
@RestController
@Tag(name = SwaggerTagConst.Support.SCRIPT_DOC)
@RequiredArgsConstructor
@RequestMapping("/script/engine")
public class ScriptEngineController {

    private final EngineFunctionRegistry engineFunctionRegistry;

    private final ScriptEngine scriptEngine;

    @Operation(summary = "【用户】脚本引擎-已注册函数文档，按业务域分组")
    @SaCheckPermission("script:query")
    @GetMapping("/view")
    public ResponseDTO<?> view() {
        return ResponseDTO.ok(engineFunctionRegistry.exportDocs());
    }

    @Operation(summary = "【用户】脚本引擎-场景契约查询，编辑器据此补全变量名")
    @SaCheckPermission("script:query")
    @GetMapping("/scene/view")
    public ResponseDTO<List<ScriptSceneDocDTO>> sceneView() {
        return ResponseDTO.ok(Arrays.stream(ScriptScene.values()).map(scene -> {
            ScriptSceneDocDTO doc = new ScriptSceneDocDTO();
            doc.setScene(scene.name());
            doc.setTitle(scene.getTitle());
            doc.setDomain(scene.getDomain().name());
            doc.setDomainTitle(scene.getDomain().getTitle());
            doc.setDescription(scene.getDescription());
            doc.setReturnType(scene.getReturnType().getSimpleName());
            doc.setParams(scene.getParams().stream().map(param -> {
                ScriptSceneDocDTO.Param item = new ScriptSceneDocDTO.Param();
                item.setName(param.name());
                item.setType(param.type().getSimpleName());
                item.setRequired(param.required());
                item.setDescription(param.description());
                item.setSignature(param.signature());
                return item;
            }).toList());
            return doc;
        }).toList());
    }

    @Operation(summary = "【用户】脚本引擎-语法校验，不执行")
    @SaCheckPermission("script:query")
    @PostMapping("/check")
    public ResponseDTO<ScriptCheckResultVO> check(@RequestBody @Valid ScriptTestForm form) {
        try {
            scriptEngine.check(ExecutableScript.untrusted("online-check", form.getScript()));
            return ResponseDTO.ok(ScriptCheckResultVO.pass());
        } catch (EngineScriptException e) {
            // 语法不合法是校验接口的正常结果，不是故障：正常返回，把行列号交给编辑器划红线
            return ResponseDTO.ok(ScriptCheckResultVO.fail(e.getScriptErrorDetail()));
        }
    }

    @Operation(summary = "【用户】脚本引擎-在线试跑")
    @SaCheckPermission("script:test")
    @PostMapping("/online/test")
    public ResponseDTO<?> onlineTest(@RequestBody @Valid ScriptTestForm form) {
        // untrusted：在线试跑的内容完全由请求决定，引擎据此关闭一切以脚本原文为 key 的缓存
        ExecutableScript script = ExecutableScript.untrusted("online-test", form.getScript());
        EngineContext context = EngineContext.create(form.getVariables());
        return ResponseDTO.ok(scriptEngine.evaluate(script, context));
    }
}
