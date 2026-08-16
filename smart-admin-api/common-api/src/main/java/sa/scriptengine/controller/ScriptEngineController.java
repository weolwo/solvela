package sa.scriptengine.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import sa.base.common.domain.ResponseDTO;
import sa.base.constant.SwaggerTagConst;
import sa.base.module.support.repeatsubmit.annoation.RepeatSubmit;
import sa.scriptengine.core.EngineFunctionRegistry;
import sa.scriptengine.domain.ExecutableScript;
import sa.scriptengine.spi.ScriptEngine;
import sa.scriptengine.spi.StandardEngineContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = SwaggerTagConst.Support.SCRIPT_DOC)
@RequiredArgsConstructor
@RequestMapping("/script/engine")
public class ScriptEngineController {

    private final EngineFunctionRegistry engineFunctionRegistry;

    private final ScriptEngine scriptEngine;

    @Operation(summary = "【用户】脚本引擎-文档查询")
    @GetMapping("/view")
    @RepeatSubmit
    public ResponseDTO view() {
        return ResponseDTO.ok(engineFunctionRegistry.exportDocs());
    }


    @Operation(summary = "【用户】脚本引擎-在线测试")
    @PostMapping("/online/test")
    @RepeatSubmit
    public ResponseDTO onlineTest(String script) {
        return ResponseDTO.ok(scriptEngine.evaluate(ExecutableScript.of("kk",script), StandardEngineContext.create()));
    }
}
