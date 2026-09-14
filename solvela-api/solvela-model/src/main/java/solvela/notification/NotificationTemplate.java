package solvela.notification;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import solvela.enums.NotificationCategoryEnum;

import java.time.LocalDateTime;

/**
 * 通知模板 实体类。
 *
 * <h3>🔴 按 (template_code, version) 不可变</h3>
 * 编辑模板 = <b>新增一个 version</b>，永远不要原地改已有行。
 *
 * <p>原因不是洁癖：{@code t_member_notification} 只存 {@code template_code + version + params}，
 * 正文是读的时候现渲染的。原地改模板 = <b>所有历史通知的显示被追溯篡改</b>：
 * 用户 1 月收到的「恭喜获得 100 积分」，6 月改了模板之后就变成另一句话了。
 * 在金额 / 奖品类消息上这是事故级的 —— 用户截图的和现在显示的对不上。
 *
 * <p>更隐蔽的一种：占位符改名（{@code ${amount}} → {@code ${score}}）。旧 params 里没有
 * {@code score} 这个 key，而 {@code SolvelaTemplateUtil} 的口径是
 * <b>解析不到的占位符原样保留</b>（它刻意的设计，防模板注入），于是用户直接看到字面的
 * {@code ${score}}。不报错、不告警。
 *
 * <p>配套的是 {@link #status} 只停用不删除：删掉一行，指向它的历史通知就再也渲染不出来。
 *
 * @Author alaric
 * @Date 2026-09-14
 * @Copyright weolwo
 */
@Data
@TableName("t_notification_template")
public class NotificationTemplate {

    /**
     * 模板编码：跨环境稳定，代码引用它。
     *
     * <p>取值见 {@code NotificationTemplateEnum} —— 那边是编译期契约，这里是运行期数据。
     */
    private String templateCode;

    /**
     * 版本号：🔴 编辑 = 新增版本，永不原地改。
     *
     * <p>{@code t_member_notification.template_version} 指向发送当时的那一版，
     * 模板再怎么改版，历史通知的措辞都锁死在当年。
     */
    private Integer version;

    /**
     * 分类：只影响 C 端 tab 分组与免打扰粒度，不参与存储决策。
     */
    private NotificationCategoryEnum category;

    /**
     * 标题模板，{@code ${key}} 占位符。渲染走 {@code SolvelaTemplateUtil}。
     */
    private String titleTemplate;

    /**
     * 正文模板，{@code ${key}} 占位符。
     */
    private String contentTemplate;

    /**
     * 本版本用到的占位符清单，json 数组。发送时校验用。
     *
     * <p>库里这份跟着 version 走，是<b>运行期校验</b>的依据；
     * {@code NotificationTemplateEnum.requiredParams} 是编译期文档。
     * 两边不一致时以本列为准 —— 理由见那个枚举的类注释。
     */
    private String paramKeys;

    /**
     * 状态：1-启用 0-停用。
     *
     * <p>🔴 <b>只停用不删除。</b> 删掉一行，所有指向它的历史通知就渲染不出来了 ——
     * 而那些通知是用户真收到过的，不该因为运营下线了一个模板就变成空白。
     */
    private Integer status;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
