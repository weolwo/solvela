package solvela.admin.module.system.codegenerator.service.variable.backend;

import solvela.admin.module.system.codegenerator.domain.form.CodeGeneratorConfigForm;
import solvela.admin.module.system.codegenerator.service.variable.CodeGenerateBaseVariableService;

import java.util.HashMap;
import java.util.Map;

/**
 * 目前暂时没用到 这是一个空实现
 *
 * @author zhoumingfa
 * @date 2024/8/13
 */
public class MenuVariableService extends CodeGenerateBaseVariableService {

    @Override
    public boolean isSupport(CodeGeneratorConfigForm form) {
        return true;
    }

    @Override
    public Map<String, Object> getInjectVariablesMap(CodeGeneratorConfigForm form) {
        return new HashMap<>(2);
    }

}
