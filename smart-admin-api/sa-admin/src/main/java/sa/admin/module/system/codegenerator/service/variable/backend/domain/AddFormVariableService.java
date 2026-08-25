package sa.admin.module.system.codegenerator.service.variable.backend.domain;

import sa.base.common.util.SmartBeanUtil;
import sa.base.common.util.SmartCollectionUtil;
import sa.base.common.util.SmartStringUtil;
import sa.admin.module.system.codegenerator.constant.CodeFrontComponentEnum;
import sa.admin.module.system.codegenerator.domain.form.CodeGeneratorConfigForm;
import sa.admin.module.system.codegenerator.domain.model.CodeField;
import sa.admin.module.system.codegenerator.domain.model.CodeInsertAndUpdate;
import sa.admin.module.system.codegenerator.domain.model.CodeInsertAndUpdateField;
import sa.admin.module.system.codegenerator.service.variable.CodeGenerateBaseVariableService;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @Author 1024创新实验室-主任:卓大
 * @Date 2022/9/29 17:20:41
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */

public class AddFormVariableService extends CodeGenerateBaseVariableService {


    @Override
    public boolean isSupport(CodeGeneratorConfigForm form) {
        CodeInsertAndUpdate insertAndUpdate = form.getInsertAndUpdate();
        return insertAndUpdate != null && insertAndUpdate.getIsSupportInsertAndUpdate() != null && insertAndUpdate.getIsSupportInsertAndUpdate();
    }

    @Override
    public Map<String, Object> getInjectVariablesMap(CodeGeneratorConfigForm form) {
        Map<String, Object> variablesMap = new HashMap<>();

        List<CodeInsertAndUpdateField> updateFieldList = form.getInsertAndUpdate().getFieldList().stream().filter(e -> Boolean.TRUE.equals(e.getInsertFlag())).collect(Collectors.toList());
        ImmutablePair<List<String>, List<Map<String, Object>>> packageListAndFields = getPackageListAndFields(updateFieldList, form);

        variablesMap.put("packageName", form.getBasic().getJavaPackageName() + ".domain.form");
        variablesMap.put("importPackageList", packageListAndFields.getLeft());
        variablesMap.put("fields", packageListAndFields.getRight());

        return variablesMap;
    }


    public ImmutablePair<List<String>, List<Map<String, Object>>> getPackageListAndFields(List<CodeInsertAndUpdateField> fields, CodeGeneratorConfigForm form) {
        if (SmartCollectionUtil.isEmpty(fields)) {
            return ImmutablePair.of(new ArrayList<>(), new ArrayList<>());
        }

        Map<String, CodeField> fieldMap = getFieldMap(form);
        HashSet<String> packageList = new HashSet<>();


        /**
         * 1、LocalDate、LocalDateTime、BigDecimal 类型的包名
         * 2、排序
         */

        List<Map<String, Object>> finalFieldList = new ArrayList<>();

        for (CodeInsertAndUpdateField field : fields) {
            CodeField codeField = fieldMap.get(field.getColumnName());
            if (codeField == null) {
                continue;
            }

            // CodeField 和 InsertAndUpdateField 合并
            Map<String, Object> finalFieldMap = SmartBeanUtil.beanToMap(field);
            finalFieldMap.putAll(SmartBeanUtil.beanToMap(codeField));

            // 枚举
            if (SmartStringUtil.isNotEmpty(codeField.getEnumName())) {
                packageList.add("import sa.base.common.swagger.SchemaEnum;");
                packageList.add("import sa.base.common.validator.enumeration.CheckEnum;");
                packageList.add("import " + form.getBasic().getJavaPackageName() + ".constant." + codeField.getEnumName() + ";");

                //enum check
                String checkEnumPrefix = "@CheckEnum(value = " + codeField.getEnumName() + ".class, message = \"" + codeField.getLabel() + " 错误\"";
                String checkEnum = checkEnumPrefix + (field.getRequiredFlag() ? ", required = true)" : ")");

                finalFieldMap.put("apiModelProperty", "@SchemaEnum(value = " + codeField.getEnumName() + ".class, desc = \"" + codeField.getLabel() + "\")");
                finalFieldMap.put("checkEnum", checkEnum);
                finalFieldMap.put("isEnum", true);

            } else {
                String prefix = "@Schema(description = \"" + codeField.getLabel() + "\"";
                String apiModelProperty = prefix + (field.getRequiredFlag() ? ", requiredMode = Schema.RequiredMode.REQUIRED)" : ")");
                finalFieldMap.put("apiModelProperty", apiModelProperty);

                packageList.add("import io.swagger.v3.oas.annotations.media.Schema;");

                if (Boolean.TRUE.equals(field.getRequiredFlag())) {
                    String notEmptyPrefix = "String".equals(codeField.getJavaType()) ? "@NotBlank" : "@NotNull";
                    finalFieldMap.put("notEmpty", "\n    " + notEmptyPrefix + "(message = \"" + codeField.getLabel() + " 不能为空\")");
                    packageList.add("String".equals(codeField.getJavaType()) ? "import jakarta.validation.constraints.NotBlank;"
                            : "import jakarta.validation.constraints.NotNull;");
                }
            }

            //字典
            if (SmartStringUtil.isNotEmpty(codeField.getDict())) {
                finalFieldMap.put("dict", "\n    @JsonDeserialize(using = DictDataDeserializer.class)");
                packageList.add("import tools.jackson.databind.annotation.JsonDeserialize;");
                packageList.add("import sa.base.common.json.deserializer.DictDataDeserializer;");
            }

            //文件上传
            if (CodeFrontComponentEnum.FILE_UPLOAD.equalsValue(field.getFrontComponent())) {
                finalFieldMap.put("file", "\n    @JsonDeserialize(using = FileKeyVoDeserializer.class)");
                packageList.add("import tools.jackson.databind.annotation.JsonDeserialize;");
                packageList.add("import sa.base.common.json.deserializer.FileKeyVoDeserializer;");
            }

            packageList.add(getJavaPackageName(codeField.getJavaType()));
            finalFieldList.add(finalFieldMap);
        }


        // lombok
        packageList.add("import lombok.Data;");

        List<String> packageNameList = packageList.stream().filter(Objects::nonNull).collect(Collectors.toList());
        Collections.sort(packageNameList);
        return ImmutablePair.of(packageNameList, finalFieldList);
    }

}
