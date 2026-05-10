package net.lab1024.sa.scriptengine.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.constant.SwaggerTagConst;
import net.lab1024.sa.base.module.support.repeatsubmit.annoation.RepeatSubmit;
import net.lab1024.sa.scriptengine.core.EngineFunctionRegistry;
import net.lab1024.sa.scriptengine.domain.ExecutableScript;
import net.lab1024.sa.scriptengine.spi.ScriptEngine;
import net.lab1024.sa.scriptengine.spi.StandardEngineContext;
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
