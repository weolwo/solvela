package solvela.admin.module.system.codegenerator.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import solvela.admin.module.system.support.SupportBaseController;
import solvela.base.domain.PageResult;
import solvela.web.SolvelaResponseUtil;
import solvela.base.constant.SwaggerTagConst;
import solvela.admin.module.system.codegenerator.domain.form.CodeGeneratorConfigForm;
import solvela.admin.module.system.codegenerator.domain.form.CodeGeneratorPreviewForm;
import solvela.admin.module.system.codegenerator.domain.form.TableQueryForm;
import solvela.admin.module.system.codegenerator.domain.vo.TableColumnVO;
import solvela.admin.module.system.codegenerator.domain.vo.TableConfigVO;
import solvela.admin.module.system.codegenerator.domain.vo.TableVO;
import solvela.admin.module.system.codegenerator.service.CodeGeneratorService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * 代码生成
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2022-06-29 20:23:46
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@Tag(name = SwaggerTagConst.Support.CODE_GENERATOR)
@Controller
public class CodeGeneratorController extends SupportBaseController {

    @Resource
    private CodeGeneratorService codeGeneratorService;

    // ------------------- 查询 -------------------

    @Operation(summary = "获取表的列 @author 卓大")
    @GetMapping("/codeGenerator/table/getTableColumns/{table}")
    @ResponseBody
    public List<TableColumnVO> getTableColumns(@PathVariable String table) {
        return codeGeneratorService.getTableColumns(table);
    }

    @Operation(summary = "查询数据库的表 @author 卓大")
    @PostMapping("/codeGenerator/table/queryTableList")
    @ResponseBody
    public PageResult<TableVO> queryTableList(@RequestBody @Valid TableQueryForm tableQueryForm) {
        return codeGeneratorService.queryTableList(tableQueryForm);
    }

    // ------------------- 配置 -------------------

    @Operation(summary = "获取表的配置信息 @author 卓大")
    @GetMapping("/codeGenerator/table/getConfig/{table}")
    @ResponseBody
    public TableConfigVO getTableConfig(@PathVariable String table) {
        return codeGeneratorService.getTableConfig(table);
    }

    @Operation(summary = "更新配置信息 @author 卓大")
    @PostMapping("/codeGenerator/table/updateConfig")
    @ResponseBody
    public void updateConfig(@RequestBody @Valid CodeGeneratorConfigForm form) {
        codeGeneratorService.updateConfig(form);
    }

    // ------------------- 生成 -------------------

    @Operation(summary = "代码预览 @author 卓大")
    @PostMapping("/codeGenerator/code/preview")
    @ResponseBody
    public String preview(@RequestBody @Valid CodeGeneratorPreviewForm form) {
        return codeGeneratorService.preview(form);
    }

    @Operation(summary = "代码下载 @author 卓大")
    @GetMapping(value = "/codeGenerator/code/download/{tableName}", produces = "application/octet-stream")
    public void download(@PathVariable String tableName, HttpServletResponse response) throws IOException {
        // 生成失败时 service 直接抛 BusinessException，由全局处理器翻成 4xx/5xx。
        // 原先是在这里判返回值、失败就往一个已经声明了 octet-stream 的响应里手写 JSON ——
        // 浏览器会照样把它当成文件存下来，用户得到一个内容是错误信息的 .zip
        byte[] zip = codeGeneratorService.download(tableName);
        SolvelaResponseUtil.setDownloadFileHeader(response, tableName + "_code.zip", (long) zip.length);
        response.getOutputStream().write(zip);
    }

}