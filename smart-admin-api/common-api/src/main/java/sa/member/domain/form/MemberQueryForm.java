package sa.member.domain.form;

import sa.base.common.domain.PageParam;
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

    @Schema(description = "账号：微信号风格，字母开头6~20位[A-Za-z][A-Za-z0-9_-]。全局唯一(大小写不敏感)，用户可改")
    private String memberName;

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
