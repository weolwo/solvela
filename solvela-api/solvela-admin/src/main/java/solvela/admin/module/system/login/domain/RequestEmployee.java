package solvela.admin.module.system.login.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import solvela.enums.GenderEnum;
import solvela.admin.constant.UserTypeEnum;
import solvela.web.swagger.SchemaEnum;

import java.io.Serializable;

/**
 * 请求员工登录信息
 *
 * @Author 1024创新实验室: 善逸
 * @Date 2021/8/4 21:15
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@Data
public class RequestEmployee implements Serializable {

    @Schema(description = "员工id")
    private Long employeeId;

    @SchemaEnum(UserTypeEnum.class)
    private UserTypeEnum userType;

    @Schema(description = "登录账号")
    private String loginName;

    @Schema(description = "员工名称")
    private String actualName;

    @Schema(description = "头像")
    private String avatar;

    @SchemaEnum(GenderEnum.class)
    private GenderEnum gender;

    @Schema(description = "手机号码")
    private String phone;

    @Schema(description = "部门id")
    private Long departmentId;

    @Schema(description = "部门名称")
    private String departmentName;

    @Schema(description = "职务级别ID")
    private Long positionId;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "是否禁用")
    private Boolean disabledFlag;

    @Schema(description = "是否为超管")
    private Boolean administratorFlag;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "请求ip")
    private String ip;

    @Schema(description = "请求user-agent")
    private String userAgent;

    /**
     * 别名：{@code employeeId}。审计字段与日志里统一叫 userId，保留一个稳定的读法，
     * 免得每个调用点都要记住「这里的 user 就是 employee」。
     */
    public Long getUserId() {
        return employeeId;
    }

    /**
     * 别名：{@code actualName} —— 落进 {@code create_by} / {@code update_by} 的就是它
     */
    public String getUserName() {
        return actualName;
    }
}
