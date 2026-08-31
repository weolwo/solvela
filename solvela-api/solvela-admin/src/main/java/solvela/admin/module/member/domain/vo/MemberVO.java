package solvela.admin.module.member.domain.vo;

import solvela.enums.GenderEnum;
import solvela.enums.MemberStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 会员主表 列表VO
 *
 * @Author weolwo
 * @Date 2026-08-22 19:39:08
 * @Copyright weolwo
 */

@Data
public class MemberVO {


    @Schema(description = "会员号：10位数字(1000000000~9999999999)。全链路关联键+迁移锚点，永不可变")
    private Long memberId;

    @Schema(description = "账号：微信号风格，字母开头6~20位[A-Za-z][A-Za-z0-9_-]。全局唯一(大小写不敏感)，用户可改")
    private String memberName;

    @Schema(description = "上次修改账号的时间：改名限频判据(建议一年一次)。为空表示从未改过")
    private LocalDateTime nameUpdateTime;

    @Schema(description = "昵称：中文随意，用户可改。?任何地方都不许拿它做关联键")
    private String nickname;

    @Schema(description = "头像 file_id（走文件模块，同商城图片）")
    private Long avatarFileId;

    @Schema(description = "性别：0-未知, 1-男, 2-女")
    private GenderEnum gender;

    @Schema(description = "生日：生日营销用，可空")
    private LocalDate birthday;

    @Schema(description = "手机号密文（AES/SM4，密钥走配置）")
    private String phone;

    @Schema(description = "手机号HMAC-SHA256原始字节(32B)：唯一约束与登录查询走它。注销时置NULL以释放号码。查看用HEX()")
    private String phoneHash;

    @Schema(description = "邮箱密文，可空")
    private String email;

    @Schema(description = "邮箱HMAC-SHA256原始字节(32B)，可空")
    private String emailHash;

    // 🔴 刻意没有 password：它是 Argon2id 哈希，2026-08-31 从本 VO 与
    // MemberMapper.xml 的 base_columns 里一并删除。管理端前端一处都没用过它，
    // 而把口令哈希送到浏览器等于把离线爆破的材料交出去。
    // 同理 phoneHash / emailHash 现在经 SQL 的 HEX() 出来（列是 binary(32)），
    // 只用于排查比对，不做展示。

    @Schema(description = "状态：1-正常, 2-冻结(风控/违规), 3-已注销")
    private MemberStatusEnum status;

    @Schema(description = "注册来源渠道：H5/APP/WECHAT/INVITE/IMPORT...")
    private String registerSource;

    @Schema(description = "注册IP：批量注册的识别依据")
    private String registerIp;

    @Schema(description = "邀请人member_id：没有邀请体系时恒为空，留着比事后加表便宜")
    private Long inviteId;

    @Schema(description = "运营备注")
    private String remark;

    @Schema(description = "创建人：后台导入时有值，自主注册为空")
    private String createBy;

    @Schema(description = "注册时间")
    private LocalDateTime createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
