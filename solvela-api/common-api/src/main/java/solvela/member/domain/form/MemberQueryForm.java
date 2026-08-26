package solvela.member.domain.form;

import solvela.base.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会员主表 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-08-22 19:39:08
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class MemberQueryForm extends PageParam {

    @Schema(description = "会员号：精确匹配")
    private Long memberId;

    /** 模糊匹配。列表页那个输入框是「搜索」，精确等于的话运营得把账号一字不差地打出来 */
    @Schema(description = "账号：模糊匹配")
    private String memberName;

    @Schema(description = "昵称：模糊匹配")
    private String nickname;

    @Schema(description = "性别：0-未知, 1-男, 2-女")
    private Integer gender;

    @Schema(description = "状态：1-正常, 2-冻结(风控/违规), 3-已注销")
    private Integer status;

    @Schema(description = "注册来源渠道：H5/APP/WECHAT/INVITE/IMPORT...")
    private String registerSource;

    @Schema(description = "邀请人memberId：没有邀请体系时恒为空，留着比事后加表便宜")
    private Long inviteId;

    @Schema(description = "注册时间")
    private LocalDate createTimeBegin;

    @Schema(description = "注册时间")
    private LocalDate createTimeEnd;

}
