package sa.base.module.support.table;

import jakarta.annotation.Resource;
import sa.base.common.domain.RequestUser;
import sa.base.common.domain.ResponseDTO;
import sa.base.common.util.SmartCollectionUtil;
import sa.base.module.support.table.domain.TableColumnEntity;
import sa.base.module.support.table.domain.TableColumnUpdateForm;
import sa.base.common.util.JsonUtils;
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
    public String getTableColumns(RequestUser requestUser, Integer tableId) {
        TableColumnEntity tableColumnEntity = tableColumnDao.selectByUserIdAndTableId(requestUser.getUserId(), requestUser.getUserType().getValue(), tableId);
        return tableColumnEntity == null ? null : tableColumnEntity.getColumns();
    }

    /**
     * 更新表格列
     *
     * @return
     */
    public ResponseDTO<String> updateTableColumns(RequestUser requestUser, TableColumnUpdateForm updateForm) {
        if (SmartCollectionUtil.isEmpty(updateForm.getColumnList())) {
            return ResponseDTO.ok();
        }
        Integer tableId = updateForm.getTableId();
        TableColumnEntity tableColumnEntity = tableColumnDao.selectByUserIdAndTableId(requestUser.getUserId(), requestUser.getUserType().getValue(), tableId);
        if (tableColumnEntity == null) {
            tableColumnEntity = new TableColumnEntity();
            tableColumnEntity.setTableId(tableId);
            tableColumnEntity.setUserId(requestUser.getUserId());
            tableColumnEntity.setUserType(requestUser.getUserType().getValue());

            tableColumnEntity.setColumns(JsonUtils.toJson(updateForm.getColumnList()));
            tableColumnDao.insert(tableColumnEntity);
        } else {
            tableColumnEntity.setColumns(JsonUtils.toJson(updateForm.getColumnList()));
            tableColumnDao.updateById(tableColumnEntity);
        }
        return ResponseDTO.ok();
    }

    /**
     * 删除表格列
     *
     * @return
     */
    public ResponseDTO<String> deleteTableColumn(RequestUser requestUser, Integer tableId) {
        tableColumnDao.deleteTableColumn(requestUser.getUserId(), requestUser.getUserType().getValue(), tableId);
        return ResponseDTO.ok();
    }
}
