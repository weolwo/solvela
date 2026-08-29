package solvela.admin.module.system.support;

import solvela.web.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import solvela.admin.module.system.support.SupportBaseController;
import solvela.base.domain.PageResult;
import solvela.admin.module.system.login.domain.RequestEmployee;
import solvela.admin.auth.CurrentEmployee;
import solvela.base.constant.SwaggerTagConst;
import solvela.admin.module.system.loginlog.LoginLogService;
import solvela.admin.module.system.loginlog.domain.LoginLogQueryForm;
import solvela.admin.module.system.loginlog.domain.LoginLogVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录日志
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2022/07/22 19:46:23
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@RestController
@Tag(name = SwaggerTagConst.Support.LOGIN_LOG)
public class AdminLoginLogController extends SupportBaseController {

    @Resource
    private LoginLogService loginLogService;

    @Operation(summary = "分页查询 @author 卓大")
    @PostMapping("/loginLog/page/query")
    @RequiresPermission("support:loginLog:query")
    public PageResult<LoginLogVO> queryByPage(@RequestBody LoginLogQueryForm queryForm) {
        return loginLogService.queryByPage(queryForm);
    }

    @Operation(summary = "分页查询当前登录人信息 @author 善逸")
    @PostMapping("/loginLog/page/query/login")
    public PageResult<LoginLogVO> queryByPageLogin(@RequestBody LoginLogQueryForm queryForm) {
        RequestEmployee requestUser = CurrentEmployee.orNull();
        queryForm.setUserId(requestUser.getUserId());
        queryForm.setUserType(requestUser.getUserType());
        return loginLogService.queryByPage(queryForm);
    }


}
