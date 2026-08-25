package sa.admin.module.system.codegenerator.service.variable.backend;

import sa.base.common.util.SmartCaseFormat;
import sa.base.common.util.SmartCollectionUtil;
import sa.base.common.util.SmartEnumUtil;
import sa.admin.module.system.codegenerator.constant.CodeDeleteEnum;
import sa.admin.module.system.codegenerator.domain.form.CodeGeneratorConfigForm;
import sa.admin.module.system.codegenerator.domain.model.CodeInsertAndUpdateField;
import sa.admin.module.system.codegenerator.service.variable.CodeGenerateBaseVariableService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @Author 1024创新实验室-主任:卓大
 * @Date 2022/9/29 17:20:41
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */

public class ControllerVariableService extends CodeGenerateBaseVariableService {

    @Override
    public boolean isSupport(CodeGeneratorConfigForm form) {
        return true;
    }

    @Override
    public Map<String, Object> getInjectVariablesMap(CodeGeneratorConfigForm form) {
        Map<String, Object> variablesMap = new HashMap<>();

        List<CodeInsertAndUpdateField> updateFieldList = form.getInsertAndUpdate().getFieldList().stream().filter(e -> Boolean.TRUE.equals(e.getInsertFlag())).collect(Collectors.toList());

        variablesMap.put("packageName", form.getBasic().getJavaPackageName() + ".controller");

        List<String> packageList = getPackageList(updateFieldList, form);
        variablesMap.put("importPackageList", packageList);

        return variablesMap;
    }


    public List<String> getPackageList(List<CodeInsertAndUpdateField> fields, CodeGeneratorConfigForm form) {
        if (SmartCollectionUtil.isEmpty(fields)) {
            return new ArrayList<>();
        }

        HashSet<String> packageSet = new HashSet<>();

        //1、javabean相关的包
        packageSet.addAll(getJavaBeanImportClass(form).stream().collect(Collectors.toList()));

        //2、其他包
        if (form.getDeleteInfo().getIsSupportDelete()) {

            CodeDeleteEnum codeDeleteEnum = SmartEnumUtil.getEnumByValue(form.getDeleteInfo().getDeleteEnum(), CodeDeleteEnum.class);
            if (codeDeleteEnum == CodeDeleteEnum.BATCH || codeDeleteEnum == CodeDeleteEnum.SINGLE_AND_BATCH) {
                //2、批量删除的话，要导入ValidateList
                packageSet.add("import sa.base.common.domain.ValidateList;");
            }

            if (codeDeleteEnum == CodeDeleteEnum.SINGLE || codeDeleteEnum == CodeDeleteEnum.SINGLE_AND_BATCH) {
                //3、单个删除的话，要导入 @PathVariable
                packageSet.add("import org.springframework.web.bind.annotation.PathVariable;");
                packageSet.add("import org.springframework.web.bind.annotation.GetMapping;");
            }
        }

        packageSet.add("import " + form.getBasic().getJavaPackageName() + ".service." + SmartCaseFormat.LOWER_CAMEL.to(SmartCaseFormat.UPPER_CAMEL, form.getBasic().getModuleName()) + "Service;");

        // 排序一下
        ArrayList<String> packageList = new ArrayList<>(packageSet);
        Collections.sort(packageList);
        return packageList;
    }

}
