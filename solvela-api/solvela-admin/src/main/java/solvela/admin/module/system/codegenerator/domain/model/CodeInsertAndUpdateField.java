package solvela.admin.module.system.codegenerator.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import solvela.base.common.swagger.SchemaEnum;
import solvela.base.common.validator.enumeration.CheckEnum;
import solvela.admin.module.system.codegenerator.constant.CodeFrontComponentEnum;
import solvela.admin.module.system.codegenerator.constant.CodeGeneratorPageTypeEnum;

/**
 * 代码生成 增加、修改的字段 模型
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2022-06-30 22:15:38
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */

@Data
public class CodeInsertAndUpdateField {

    @NotBlank(message = "3.增加、修改 列名 不能为空")
    @Schema(description = "列名")
    private String columnName;

    @NotNull(message = "3.增加、修改  必须 不能为空")
    @Schema(description = "必须")
    private Boolean requiredFlag;

    @NotNull(message = "3.增加、修改  插入标识 不能为空")
    @Schema(description = "插入标识")
    private Boolean insertFlag;

    @NotNull(message = "3.增加、修改  更新标识 不能为空")
    @Schema(description = "更新标识")
    private Boolean updateFlag;

    @SchemaEnum(value = CodeFrontComponentEnum.class)
    @CheckEnum(value = CodeFrontComponentEnum.class, message = "3.增加、修改 组件类型 枚举值错误", required = true)
    private String frontComponent;

}
