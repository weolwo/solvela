package solvela.admin.module.system.codegenerator.service.variable.front;

import solvela.base.util.SolvelaBeanUtil;
import solvela.base.util.SolvelaCaseFormat;
import solvela.base.util.SolvelaStringUtil;
import solvela.admin.module.system.codegenerator.constant.CodeFrontComponentEnum;
import solvela.admin.module.system.codegenerator.domain.form.CodeGeneratorConfigForm;
import solvela.admin.module.system.codegenerator.domain.model.CodeField;
import solvela.admin.module.system.codegenerator.domain.model.CodeInsertAndUpdateField;
import solvela.admin.module.system.codegenerator.service.variable.CodeGenerateBaseVariableService;

import java.util.*;

/**
 * @Author 1024创新实验室-主任:卓大
 * @Date 2022/9/29 17:20:41
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */

public class FormVariableService extends CodeGenerateBaseVariableService {

    @Override
    public boolean isSupport(CodeGeneratorConfigForm form) {
        return true;
    }

    @Override
    public Map<String, Object> getInjectVariablesMap(CodeGeneratorConfigForm form) {
        Map<String, Object> variablesMap = new HashMap<>();
        List<Map<String, Object>> fieldsVariableList = new ArrayList<>();
        List<CodeInsertAndUpdateField> fieldList = form.getInsertAndUpdate().getFieldList();

        HashSet<String> frontImportSet = new HashSet<>();

        for (CodeInsertAndUpdateField field : fieldList) {
            // 不存在 添加 和 更新
            if (!(field.getInsertFlag() || field.getUpdateFlag())) {
                continue;
            }

            Map<String, Object> objectMap = SolvelaBeanUtil.beanToMap(field);

            CodeField codeField = getCodeFieldByColumnName(field.getColumnName(), form);
            if (codeField == null) {
                continue;
            }
            objectMap.put("label", codeField.getLabel());
            objectMap.put("fieldName", codeField.getFieldName());
            objectMap.put("dict", codeField.getDict());

            if (SolvelaStringUtil.isNotBlank(codeField.getEnumName())) {
                String upperUnderscoreEnum = SolvelaCaseFormat.UPPER_CAMEL.to(SolvelaCaseFormat.UPPER_UNDERSCORE, codeField.getEnumName());
                objectMap.put("upperUnderscoreEnum", upperUnderscoreEnum);
            }

            fieldsVariableList.add(objectMap);

            if (CodeFrontComponentEnum.ENUM_SELECT.equalsValue(field.getFrontComponent())) {
                frontImportSet.add("import SolvelaEnumSelect from '/@/components/framework/solvela-enum-select/index.vue';");
            }

            if (CodeFrontComponentEnum.BOOLEAN_SELECT.equalsValue(field.getFrontComponent())) {
                frontImportSet.add("import BooleanSelect from '/@/components/framework/boolean-select/index.vue';");
            }

            if (CodeFrontComponentEnum.DICT_SELECT.equalsValue(field.getFrontComponent())) {
                frontImportSet.add("import DictSelect from '/@/components/support/dict-select/index.vue';");
                frontImportSet.add("import { DICT_CODE_ENUM } from '/@/constants/support/dict-const.js';");
            }

            if (CodeFrontComponentEnum.FILE_UPLOAD.equalsValue(field.getFrontComponent())) {
                frontImportSet.add("import { FILE_FOLDER_TYPE_ENUM } from '/@/constants/support/file-const';");
                frontImportSet.add("import FileUpload from '/@/components/support/file-upload/index.vue';");
            }
        }

        variablesMap.put("formFields", fieldsVariableList);
        variablesMap.put("frontImportList", new ArrayList<>(frontImportSet));

        return variablesMap;
    }
}
