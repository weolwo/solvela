package solvela.admin.module.system.codegenerator.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import solvela.base.common.domain.PageResult;
import solvela.base.common.domain.ResponseDTO;
import solvela.base.common.util.SolvelaCollectionUtil;
import solvela.base.common.util.SolvelaPageUtil;
import solvela.base.common.util.SolvelaStringUtil;
import solvela.admin.module.system.codegenerator.constant.CodeGeneratorConstant;
import solvela.admin.module.system.codegenerator.dao.CodeGeneratorConfigDao;
import solvela.admin.module.system.codegenerator.dao.CodeGeneratorDao;
import solvela.admin.module.system.codegenerator.domain.entity.CodeGeneratorConfigEntity;
import solvela.admin.module.system.codegenerator.domain.form.CodeGeneratorConfigForm;
import solvela.admin.module.system.codegenerator.domain.form.CodeGeneratorPreviewForm;
import solvela.admin.module.system.codegenerator.domain.form.TableQueryForm;
import solvela.admin.module.system.codegenerator.domain.model.*;
import solvela.admin.module.system.codegenerator.domain.vo.TableColumnVO;
import solvela.admin.module.system.codegenerator.domain.vo.TableConfigVO;
import solvela.admin.module.system.codegenerator.domain.vo.TableVO;
import solvela.base.common.util.JsonUtils;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;

/**
 * 代码生成器 Service
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2022-06-30 22:15:38
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@Slf4j
@Service
public class CodeGeneratorService {

    private static final String COLUMN_NO_NULLABLE_IDENTIFY = "NO";

    private static final String COLUMN_PRIMARY_KEY = "PRI";

    private static final String COLUMN_AUTO_INCREASE = "auto_increment";

    @Resource
    private CodeGeneratorDao codeGeneratorDao;

    @Resource
    private CodeGeneratorConfigDao codeGeneratorConfigDao;

    @Resource
    private CodeGeneratorTemplateService codeGeneratorTemplateService;


    /**
     * 列信息
     *
     * @param tableName
     * @return
     */
    public List<TableColumnVO> getTableColumns(String tableName) {
        List<TableColumnVO> tableColumns = codeGeneratorDao.selectTableColumn(tableName);
        for (TableColumnVO tableColumn : tableColumns) {
            tableColumn.setNullableFlag(!COLUMN_NO_NULLABLE_IDENTIFY.equalsIgnoreCase(tableColumn.getIsNullable()));
            tableColumn.setPrimaryKeyFlag(COLUMN_PRIMARY_KEY.equalsIgnoreCase(tableColumn.getColumnKey()));
            tableColumn.setAutoIncreaseFlag(SolvelaStringUtil.isNotEmpty(tableColumn.getExtra()) && COLUMN_AUTO_INCREASE.equalsIgnoreCase(tableColumn.getExtra()));
        }
        return tableColumns;
    }


    /**
     * 查询数据库表数据
     *
     * @param tableQueryForm
     * @return
     */
    public PageResult<TableVO> queryTableList(TableQueryForm tableQueryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(tableQueryForm);
        List<TableVO> tableVOList = codeGeneratorDao.queryTableList(page, tableQueryForm);
        return SolvelaPageUtil.convert2PageResult(page, tableVOList);
    }

    /**
     * 获取 表的 配置信息
     *
     * @param table
     * @return
     */
    public TableConfigVO getTableConfig(String table) {

        TableConfigVO config = new TableConfigVO();

        CodeGeneratorConfigEntity codeGeneratorConfigEntity = codeGeneratorConfigDao.selectById(table);
        if (codeGeneratorConfigEntity == null) {
            return config;
        }

        if (SolvelaStringUtil.isNotEmpty(codeGeneratorConfigEntity.getBasic())) {
            CodeBasic basic = JsonUtils.parseObject(codeGeneratorConfigEntity.getBasic(), CodeBasic.class);
            config.setBasic(basic);
        }

        if (SolvelaStringUtil.isNotEmpty(codeGeneratorConfigEntity.getFields())) {
            List<CodeField> fields = JsonUtils.parseList(codeGeneratorConfigEntity.getFields(), CodeField.class);
            config.setFields(fields);
        }

        if (SolvelaStringUtil.isNotEmpty(codeGeneratorConfigEntity.getInsertAndUpdate())) {
            CodeInsertAndUpdate insertAndUpdate = JsonUtils.parseObject(codeGeneratorConfigEntity.getInsertAndUpdate(), CodeInsertAndUpdate.class);
            config.setInsertAndUpdate(insertAndUpdate);
        }

        if (SolvelaStringUtil.isNotEmpty(codeGeneratorConfigEntity.getDeleteInfo())) {
            CodeDelete deleteInfo = JsonUtils.parseObject(codeGeneratorConfigEntity.getDeleteInfo(), CodeDelete.class);
            config.setDeleteInfo(deleteInfo);
        }

        if (SolvelaStringUtil.isNotEmpty(codeGeneratorConfigEntity.getQueryFields())) {
            List<CodeQueryField> queryFields = JsonUtils.parseList(codeGeneratorConfigEntity.getQueryFields(), CodeQueryField.class);
            config.setQueryFields(queryFields);
        }

        if (SolvelaStringUtil.isNotEmpty(codeGeneratorConfigEntity.getTableFields())) {
            List<CodeTableField> tableFields = JsonUtils.parseList(codeGeneratorConfigEntity.getTableFields(), CodeTableField.class);
            config.setTableFields(tableFields);
        }

        return config;
    }

    /**
     * 更新配置
     *
     * @param form
     * @return
     */
    public synchronized ResponseDTO<String> updateConfig(CodeGeneratorConfigForm form) {
        long existCount = codeGeneratorDao.countByTableName(form.getTableName());
        if (existCount == 0) {
            return ResponseDTO.userErrorParam("表不存在，请联系后端查看下数据库");
        }

        CodeGeneratorConfigEntity codeGeneratorConfigEntity = codeGeneratorConfigDao.selectById(form.getTableName());
        boolean updateFlag = true;
        if (codeGeneratorConfigEntity == null) {
            codeGeneratorConfigEntity = new CodeGeneratorConfigEntity();
            updateFlag = false;
        }

        // 校验假删，必须有 deleted_flag 字段
        List<TableColumnVO> tableColumns = getTableColumns(form.getTableName());
        if (null != form.getDeleteInfo() && form.getDeleteInfo().getIsSupportDelete() && !form.getDeleteInfo().getIsPhysicallyDeleted()) {
            Optional<TableColumnVO> any = tableColumns.stream().filter(e -> e.getColumnName().equals(CodeGeneratorConstant.DELETED_FLAG)).findAny();
            if (!any.isPresent()) {
                return ResponseDTO.userErrorParam("表结构中没有假删字段：" + CodeGeneratorConstant.DELETED_FLAG + ",请仔细排查");
            }
        }

        // 校验表必须有主键
        if (tableColumns.stream().noneMatch(e -> COLUMN_PRIMARY_KEY.equalsIgnoreCase(e.getColumnKey()))) {
            return ResponseDTO.userErrorParam("表必须有主键，请联系后端查看下数据库表结构");
        }

        codeGeneratorConfigEntity.setTableName(form.getTableName());
        codeGeneratorConfigEntity.setBasic(JsonUtils.toJson(form.getBasic()));
        codeGeneratorConfigEntity.setFields(JsonUtils.toJson(form.getFields()));
        codeGeneratorConfigEntity.setInsertAndUpdate(JsonUtils.toJson(form.getInsertAndUpdate()));
        codeGeneratorConfigEntity.setDeleteInfo(JsonUtils.toJson(form.getDeleteInfo()));
        codeGeneratorConfigEntity.setQueryFields(JsonUtils.toJson(form.getQueryFields()));
        codeGeneratorConfigEntity.setTableFields(JsonUtils.toJson(form.getTableFields()));

        if (updateFlag) {
            codeGeneratorConfigDao.updateById(codeGeneratorConfigEntity);
        } else {
            codeGeneratorConfigDao.insert(codeGeneratorConfigEntity);
        }
        return ResponseDTO.ok();
    }

    /**
     * 预览
     *
     * @param form
     * @return
     */
    public ResponseDTO<String> preview(CodeGeneratorPreviewForm form) {
        long existCount = codeGeneratorDao.countByTableName(form.getTableName());
        if (existCount == 0) {
            return ResponseDTO.userErrorParam("表不存在，请联系后端查看下数据库");
        }

        CodeGeneratorConfigEntity codeGeneratorConfigEntity = codeGeneratorConfigDao.selectById(form.getTableName());
        if (codeGeneratorConfigEntity == null) {
            return ResponseDTO.userErrorParam("配置信息不存在，请先进行配置");
        }

        List<TableColumnVO> columns = getTableColumns(form.getTableName());
        if (SolvelaCollectionUtil.isEmpty(columns)) {
            return ResponseDTO.userErrorParam("表没有列信息无法生成");
        }

        String result = codeGeneratorTemplateService.generate(form.getTableName(), form.getTemplateFile(), codeGeneratorConfigEntity);
        return ResponseDTO.ok(result);

    }

    /**
     * 下载代码
     *
     * @param tableName
     * @return
     */
    public ResponseDTO<byte[]> download(String tableName) {
        if (SolvelaStringUtil.isBlank(tableName)) {
            return ResponseDTO.userErrorParam("表名不能为空");
        }

        long existCount = codeGeneratorDao.countByTableName(tableName);
        if (existCount == 0) {
            return ResponseDTO.userErrorParam("表不存在，请联系后端查看下数据库");
        }

        CodeGeneratorConfigEntity codeGeneratorConfigEntity = codeGeneratorConfigDao.selectById(tableName);
        if (codeGeneratorConfigEntity == null) {
            return ResponseDTO.userErrorParam("配置信息不存在，请先进行配置");
        }

        List<TableColumnVO> columns = getTableColumns(tableName);
        if (SolvelaCollectionUtil.isEmpty(columns)) {
            return ResponseDTO.userErrorParam("表没有列信息无法生成");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        codeGeneratorTemplateService.zipGeneratedFiles(out, tableName, codeGeneratorConfigEntity);
        return ResponseDTO.ok(out.toByteArray());
    }
}