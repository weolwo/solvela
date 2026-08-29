package solvela.admin.module.system.table;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import solvela.admin.module.system.support.SupportBaseController;
import solvela.web.ResponseDTO;
import solvela.admin.auth.CurrentEmployee;
import solvela.base.constant.SwaggerTagConst;
import solvela.admin.module.system.repeatsubmit.annotation.RepeatSubmit;
import solvela.admin.module.system.table.domain.TableColumnUpdateForm;
import org.springframework.web.bind.annotation.*;

/**
 * 表格自定义列（前端用户自定义表格列，并保存到数据库里）
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2022-08-12 22:52:21
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
@RestController
@Tag(name = SwaggerTagConst.Support.TABLE_COLUMN)
public class TableColumnController extends SupportBaseController {

    @Resource
    private TableColumnService tableColumnService;

    @Operation(summary = "修改表格列 @author 卓大")
    @PostMapping("/tableColumn/update")
    @RepeatSubmit
    public ResponseDTO<String> updateTableColumn(@RequestBody @Valid TableColumnUpdateForm updateForm) {
        return tableColumnService.updateTableColumns(CurrentEmployee.orNull(), updateForm);
    }

    @Operation(summary = "恢复默认（删除） @author 卓大")
    @GetMapping("/tableColumn/delete/{tableId}")
    @RepeatSubmit
    public ResponseDTO<String> deleteTableColumn(@PathVariable Integer tableId) {
        return tableColumnService.deleteTableColumn(CurrentEmployee.orNull(), tableId);
    }

    @Operation(summary = "查询表格列 @author 卓大")
    @GetMapping("/tableColumn/getColumns/{tableId}")
    public ResponseDTO<String> getColumns(@PathVariable Integer tableId) {
        return ResponseDTO.ok(tableColumnService.getTableColumns(CurrentEmployee.orNull(), tableId));
    }
}
