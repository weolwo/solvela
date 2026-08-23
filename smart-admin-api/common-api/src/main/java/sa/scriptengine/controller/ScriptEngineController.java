package sa.scriptengine.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sa.base.common.domain.ResponseDTO;
import sa.base.constant.SwaggerTagConst;
import sa.scriptengine.controller.form.ScriptTestForm;
import sa.scriptengine.core.EngineFunctionRegistry;
import sa.scriptengine.domain.ExecutableScript;
import sa.scriptengine.spi.EngineContext;
import sa.scriptengine.spi.ScriptEngine;

@RestController
@Tag(name = SwaggerTagConst.Support.SCRIPT_DOC)
@RequiredArgsConstructor
@RequestMapping("/script/engine")
public class ScriptEngineController {

    private final EngineFunctionRegistry engineFunctionRegistry;

    private final ScriptEngine scriptEngine;

    @Operation(summary = "【用户】脚本引擎-已注册函数文档，按业务域分组")
    @GetMapping("/view")
    public ResponseDTO<?> view() {
        return ResponseDTO.ok(engineFunctionRegistry.exportDocs());
    }

    @Operation(summary = "【用户】脚本引擎-语法校验，不执行")
    @PostMapping("/check")
    public ResponseDTO<?> check(@RequestBody @Valid ScriptTestForm form) {
        scriptEngine.check(ExecutableScript.ofAdHoc("online-check", form.getScript()));
        return ResponseDTO.ok();
    }

    @Operation(summary = "【用户】脚本引擎-在线试跑")
    @PostMapping("/online/test")
    public ResponseDTO<?> onlineTest(@RequestBody @Valid ScriptTestForm form) {
        // ofAdHoc：在线试跑的内容完全不受控，绝不能进编译缓存，否则调试几十次就是几十份常驻 AST
        ExecutableScript script = ExecutableScript.ofAdHoc("online-test", form.getScript());
        EngineContext context = EngineContext.create(form.getVariables());
        return ResponseDTO.ok(scriptEngine.evaluate(script, context));
    }
}
