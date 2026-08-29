package solvela.admin.module.system.table;

import jakarta.annotation.Resource;
import solvela.admin.module.system.login.domain.RequestEmployee;
import solvela.base.util.SolvelaCollectionUtil;
import solvela.admin.module.system.table.domain.TableColumnEntity;
import solvela.admin.module.system.table.domain.TableColumnUpdateForm;
import solvela.base.json.JsonUtils;
import org.springframework.stereotype.Service;

/**
 * 表格自定义列（前端用户自定义表格列，并保存到数据库里）
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2022-08-12 22:52:21
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@Service
public class TableColumnService {

    @Resource
    private TableColumnDao tableColumnDao;

    /**
     * 获取 - 表格列
     *
     * @return
     */
    public String getTableColumns(RequestEmployee requestUser, Integer tableId) {
        TableColumnEntity tableColumnEntity = tableColumnDao.selectByUserIdAndTableId(requestUser.getUserId(), requestUser.getUserType(), tableId);
        return tableColumnEntity == null ? null : tableColumnEntity.getColumns();
    }

    /**
     * 更新表格列
     *
     * @return
     */
    public void updateTableColumns(RequestEmployee requestUser, TableColumnUpdateForm updateForm) {
        if (SolvelaCollectionUtil.isEmpty(updateForm.getColumnList())) {
            return;
        }
        Integer tableId = updateForm.getTableId();
        TableColumnEntity tableColumnEntity = tableColumnDao.selectByUserIdAndTableId(requestUser.getUserId(), requestUser.getUserType(), tableId);
        if (tableColumnEntity == null) {
            tableColumnEntity = new TableColumnEntity();
            tableColumnEntity.setTableId(tableId);
            tableColumnEntity.setUserId(requestUser.getUserId());
            tableColumnEntity.setUserType(requestUser.getUserType());

            tableColumnEntity.setColumns(JsonUtils.toJson(updateForm.getColumnList()));
            tableColumnDao.insert(tableColumnEntity);
        } else {
            tableColumnEntity.setColumns(JsonUtils.toJson(updateForm.getColumnList()));
            tableColumnDao.updateById(tableColumnEntity);
        }
    }

    /**
     * 删除表格列
     *
     * @return
     */
    public void deleteTableColumn(RequestEmployee requestUser, Integer tableId) {
        tableColumnDao.deleteTableColumn(requestUser.getUserId(), requestUser.getUserType(), tableId);
    }
}
