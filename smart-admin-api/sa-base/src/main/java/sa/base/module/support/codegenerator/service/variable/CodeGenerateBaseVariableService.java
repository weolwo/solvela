package sa.base.module.support.codegenerator.service.variable;

import sa.base.common.util.SmartCaseFormat;
import sa.base.common.util.SmartCollectionUtil;
import sa.base.common.util.SmartStringUtil;
import sa.base.module.support.codegenerator.constant.CodeFrontComponentEnum;
import sa.base.module.support.codegenerator.domain.form.CodeGeneratorConfigForm;
import sa.base.module.support.codegenerator.domain.model.CodeField;
import sa.base.module.support.codegenerator.domain.model.CodeInsertAndUpdate;
import sa.base.module.support.codegenerator.domain.model.CodeInsertAndUpdateField;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @Author 1024创新实验室-主任:卓大
 * @Date 2022/9/29 17:20:41
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
public abstract class CodeGenerateBaseVariableService {

    public abstract Map<String, Object> getInjectVariablesMap(CodeGeneratorConfigForm form);

    /**
     * 是否支持 :
     * 1、增加、修改
     * 2、删除
     *
     * @param form
     * @return
     */
    public abstract boolean isSupport(CodeGeneratorConfigForm form);

    /**
     * 获取所有javabean的 import 包名
     *
     * @param form
     * @return
     */
    public List<String> getJavaBeanImportClass(CodeGeneratorConfigForm form) {
        String upperCamelName = SmartCaseFormat.UPPER_CAMEL.to(SmartCaseFormat.UPPER_CAMEL, form.getBasic().getModuleName());
        ArrayList<String> list = new ArrayList<>();

        list.add("import " + form.getBasic().getJavaPackageName() + ".domain.entity." + upperCamelName + ";");

        list.add("import " + form.getBasic().getJavaPackageName() + ".domain.form." + upperCamelName + "AddForm;");
        list.add("import " + form.getBasic().getJavaPackageName() + ".domain.form." + upperCamelName + "UpdateForm;");
        list.add("import " + form.getBasic().getJavaPackageName() + ".domain.form." + upperCamelName + "QueryForm;");

        list.add("import " + form.getBasic().getJavaPackageName() + ".domain.vo." + upperCamelName + "VO;");
        return list;
    }


    /**
     * 根据列名查找 CodeField
     */
    public CodeField getCodeFieldByColumnName(String columnName, CodeGeneratorConfigForm form) {
        List<CodeField> fields = form.getFields();
        if (SmartCollectionUtil.isEmpty(fields)) {
            return null;
        }

        return fields.stream().filter(e -> SmartStringUtil.equals(columnName, e.getColumnName())).findFirst().orElse(null);
    }


    /**
     * 是否为文件上传字段
     */
    protected boolean isFile(String columnName, CodeGeneratorConfigForm form) {
        CodeInsertAndUpdate insertAndUpdate = form.getInsertAndUpdate();
        if (insertAndUpdate == null) {
            return false;
        }

        List<CodeInsertAndUpdateField> fieldList = insertAndUpdate.getFieldList();
        if (SmartCollectionUtil.isEmpty(fieldList)) {
            return false;
        }

        Optional<CodeInsertAndUpdateField> first = fieldList.stream().filter(e -> columnName.equals(e.getColumnName())).findFirst();
        if (!first.isPresent()) {
            return false;
        }

        CodeInsertAndUpdateField field = first.get();
        return CodeFrontComponentEnum.FILE_UPLOAD.equalsValue(field.getFrontComponent());
    }

    /**
     * 是否为 字典
     */
    protected boolean isDict(String columnName, CodeGeneratorConfigForm form) {
        CodeField codeField = getCodeField(columnName, form);
        return codeField != null && codeField.getDict() != null;
    }

    /**
     * 是否为 枚举
     */
    protected boolean isEnum(String columnName, CodeGeneratorConfigForm form) {
        CodeField codeField = getCodeField(columnName, form);
        return codeField != null && codeField.getEnumName() != null;
    }

    private CodeField getCodeField(String columnName, CodeGeneratorConfigForm form) {
        List<CodeField> fields = form.getFields();
        if (SmartCollectionUtil.isEmpty(fields)) {
            return null;
        }

        return fields.stream().filter(e -> columnName.equals(e.getColumnName())).findFirst().orElse(null);
    }

    /**
     * 获取字段集合
     *
     * @param form
     * @return
     */
    protected Map<String, CodeField> getFieldMap(CodeGeneratorConfigForm form) {
        List<CodeField> fields = form.getFields();
        if (fields == null) {
            return new HashMap<>();
        }

        return fields.stream().collect(Collectors.toMap(CodeField::getColumnName, Function.identity()));
    }

    /**
     * 获取java类型
     *
     * @return
     */
    protected String getJavaPackageName(String javaType) {
        if ("BigDecimal".equals(javaType)) {
            return "import java.math.BigDecimal;";
        } else if ("LocalDate".equals(javaType)) {
            return "import java.time.LocalDate;";
        } else if ("LocalDateTime".equals(javaType)) {
            return "import java.time.LocalDateTime;";
        } else {
            return null;
        }
    }

}
