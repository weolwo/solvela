package solvela.admin.module.system.support;

import solvela.web.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import solvela.base.domain.PageResult;
import solvela.web.ResponseDTO;
import solvela.base.constant.SwaggerTagConst;
import solvela.base.module.config.ConfigService;
import solvela.base.module.config.domain.ConfigAddForm;
import solvela.base.module.config.domain.ConfigQueryForm;
import solvela.base.module.config.domain.ConfigUpdateForm;
import solvela.base.module.config.domain.ConfigVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配置
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2022-03-14 20:46:27
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@Tag(name = SwaggerTagConst.Support.CONFIG)
@RestController
public class AdminConfigController extends SupportBaseController {

    @Resource
    private ConfigService configService;

    @Operation(summary = "分页查询系统配置 @author 卓大")
    @PostMapping("/config/query")
    @RequiresPermission("support:config:query")
    public ResponseDTO<PageResult<ConfigVO>> queryConfigPage(@RequestBody @Valid ConfigQueryForm queryForm) {
        return ResponseDTO.ok(configService.queryConfigPage(queryForm));
    }

    @Operation(summary = "添加配置参数 @author 卓大")
    @PostMapping("/config/add")
    @RequiresPermission("support:config:addProposal")
    public ResponseDTO<String> addConfig(@RequestBody @Valid ConfigAddForm configAddForm) {
        configService.add(configAddForm);
        return ResponseDTO.ok();
    }

    @Operation(summary = "修改配置参数 @author 卓大")
    @PostMapping("/config/update")
    @RequiresPermission("support:config:update")
    public ResponseDTO<String> updateConfig(@RequestBody @Valid ConfigUpdateForm updateForm) {
        configService.updateConfig(updateForm);
        return ResponseDTO.ok();
    }

}
