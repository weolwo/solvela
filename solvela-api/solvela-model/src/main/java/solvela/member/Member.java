package solvela.member;

import solvela.enums.MemberStatusEnum;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 会员主表 实体类
 *
 * @Author weolwo
 * @Date 2026-08-22 19:39:08
 * @Copyright weolwo
 */

@Data
@TableName("t_member")
public class Member {

    /**
     * 会员号：10位数字(1000000000~9999999999)。全链路关联键+迁移锚点，永不可变
     */
    @TableId
    private Long memberId;

    /**
     * 账号：微信号风格，字母开头6~20位[A-Za-z][A-Za-z0-9_-]。全局唯一(大小写不敏感)，用户可改
     */
    private String memberName;

    /**
     * 上次修改账号的时间：改名限频判据(建议一年一次)。为空表示从未改过
     */
    private LocalDateTime nameUpdateTime;

    /**
     * 昵称：中文随意，用户可改。?任何地方都不许拿它做关联键
     */
    private String nickname;

    /**
     * 头像 file_id（走文件模块，同商城图片）
     */
    private Long avatarFileId;

    /**
     * 性别：0-未知, 1-男, 2-女
     */
    private Integer gender;

    /**
     * 生日：生日营销用，可空
     */
    private LocalDate birthday;

    /**
     * 手机号密文（AES/SM4，密钥走配置）
     */
    private String phone;

    /**
     * 手机号HMAC-SHA256原始字节(32B)：唯一约束与登录查询走它。注销时置NULL以释放号码。查看用HEX()
     */
    private String phoneHash;

    /**
     * 邮箱密文，可空
     */
    private String email;

    /**
     * 邮箱HMAC-SHA256原始字节(32B)，可空
     */
    private String emailHash;

    /**
     * 登录密码：Argon2id PHC串(盐已内嵌，不要再开salt列)。验证码登录可为空
     */
    private String password;

    /**
     * 状态：1-正常, 2-冻结(风控/违规), 3-已注销
     */
    private MemberStatusEnum status;

    /**
     * 注册来源渠道：H5/APP/WECHAT/INVITE/IMPORT...
     */
    private String registerSource;

    /**
     * 注册IP：批量注册的识别依据
     */
    private String registerIp;

    /**
     * 邀请人member_id：没有邀请体系时恒为空，留着比事后加表便宜
     */
    private Long inviteId;

    /**
     * 运营备注
     *
     * <p>{@code ALWAYS}：运营清空备注时必须真的写回 null。默认的 NOT_NULL 策略会把这条 set
     * 从 update 语句里静默去掉 —— 表现是点了清空、提示保存成功、刷新后备注又回来了。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    /**
     * 创建人：后台导入时有值，自主注册为空
     */
    private String createBy;

    /**
     * 注册时间
     */
    private LocalDateTime createTime;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
