package solvela.admin.module.system.codegenerator.service.variable.backend.domain;

import solvela.base.util.SolvelaCollectionUtil;
import solvela.admin.module.system.codegenerator.domain.form.CodeGeneratorConfigForm;
import solvela.admin.module.system.codegenerator.domain.model.CodeField;
import solvela.admin.module.system.codegenerator.service.variable.CodeGenerateBaseVariableService;

import java.util.*;
import java.util.stream.Collectors;
import java.util.ArrayList;

/**
 * @Author 1024创新实验室-主任:卓大
 * @Date 2022/9/29 17:20:41
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */

public class EntityVariableService extends CodeGenerateBaseVariableService {

    @Override
    public boolean isSupport(CodeGeneratorConfigForm form) {
        return true;
    }

    @Override
    public Map<String, Object> getInjectVariablesMap(CodeGeneratorConfigForm form) {
        Map<String, Object> variablesMap = new HashMap<>();


        variablesMap.put("packageName", form.getBasic().getJavaPackageName() + ".domain.entity");
        variablesMap.put("importPackageList", getImportPackageList(form.getFields()));


        return variablesMap;
    }


    public List<String> getImportPackageList(List<CodeField> fields) {
        if (SolvelaCollectionUtil.isEmpty(fields)) {
            return new ArrayList<>();
        }

        /**
         * 1、LocalDate、LocalDateTime、BigDecimal 类型的包名
         * 2、排序
         */
        List<String> result = fields.stream().map(e -> getJavaPackageName(e.getJavaType())).filter(Objects::nonNull).distinct().collect(Collectors.toList());

        // lombok
        result.add("import lombok.Data;");

        // mybatis plus
        result.add("import com.baomidou.mybatisplus.annotation.TableName;");

        // ⚠️ 这里曾为 create_time / update_time 补 FieldFill + TableField 的 import，
        //    配合模板里硬编码的 @TableField(fill = ...) 一起产出。已随铁律 9 移除：
        //    时间列一律由 DDL 的 DEFAULT CURRENT_TIMESTAMP / ON UPDATE 生成，代码不填。

        //主键
        boolean isExistPrimaryKey = fields.stream().anyMatch(e -> e.getPrimaryKeyFlag() != null && e.getPrimaryKeyFlag());
        if (isExistPrimaryKey) {
            result.add("import com.baomidou.mybatisplus.annotation.TableId;");
        }

        //自增
        boolean isExistAutoIncrease = fields.stream().anyMatch(e -> e.getAutoIncreaseFlag() != null && e.getAutoIncreaseFlag());
        if (isExistAutoIncrease) {
            result.add("import com.baomidou.mybatisplus.annotation.IdType;");
        }

        Collections.sort(result);
        return result;
    }

}
