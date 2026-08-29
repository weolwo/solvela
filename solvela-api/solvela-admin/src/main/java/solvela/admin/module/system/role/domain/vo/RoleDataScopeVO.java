package solvela.admin.module.system.role.domain.vo;

import solvela.admin.module.system.datascope.constant.DataScopeViewTypeEnum;
import solvela.admin.module.system.datascope.constant.DataScopeTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 角色的数据范围
 *
 * @Author 1024创新实验室: 罗伊
 * @Date 2022-04-08 21:53:04
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@Data
public class RoleDataScopeVO {

    @Schema(description = "数据范围id")
    private DataScopeTypeEnum dataScopeType;

    @Schema(description = "可见范围")
    private DataScopeViewTypeEnum viewType;
}
