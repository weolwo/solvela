package solvela.notification.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import solvela.notification.NotificationTemplate;

import java.util.List;

/**
 * 通知模板 Dao。
 *
 * <p>⚠️ 这张表<b>没有写接口</b>（除了 MyBatis-Plus 基类自带的）。模板的新增与改版
 * 由管理端走，而管理端要做的是「新增一个 version」而不是 update —— 那条路径
 * 在阶段 1 还没有 UI，暂时靠 SQL 脚本灌。见方案 §11.3。
 *
 * @Author alaric
 * @Date 2026-09-14
 * @Copyright weolwo
 */
@Mapper
public interface NotificationTemplateDao extends BaseMapper<NotificationTemplate> {

    /**
     * 取某个模板<b>当前启用的最新版本</b>。发送时用它决定「这次用哪一版」。
     *
     * <p>取到之后版本号就被写进 {@code t_member_notification.template_version} 锁死了，
     * 之后模板再改版也不影响这一条。
     *
     * @return 没有启用版本时返回 null —— 调用方必须处理，不要假设模板一定在
     */
    NotificationTemplate selectLatestEnabled(@Param("templateCode") String templateCode);

    /**
     * 按 (code, version) 精确取一版，渲染历史通知用。
     *
     * <p>这里<b>不过滤 status</b>：模板被停用之后，指向它的历史通知仍然要能渲染出来。
     * 停用的意思是「以后不再用它发新通知」，不是「过去发的都作废」。
     */
    NotificationTemplate selectByCodeAndVersion(@Param("templateCode") String templateCode,
                                                @Param("version") Integer version);

    /**
     * 一次把所有启用中的模板捞出来，供进程内缓存预热。
     */
    List<NotificationTemplate> selectAllEnabled();

    /**
     * 某个编码的当前最大版本号。新增版本时用它 +1。
     *
     * <p>🔴 <b>不过滤 status</b>：停用过的版本也占着号。过滤掉的话，
     * 停用 v2 之后新增会又拿到 2，撞主键 —— 而报出来的是个
     * {@code DuplicateKeyException}，根因一点都不明显。
     *
     * @return 没有任何版本时返回 null
     */
    Integer selectMaxVersion(@Param("templateCode") String templateCode);

    /**
     * 停用某一版。
     *
     * <h3>🔴 为什么不能用 MyBatis-Plus 的 {@code updateById}</h3>
     * {@link solvela.notification.NotificationTemplate} 的主键是<b>复合</b>的
     * （{@code template_code} + {@code version}），实体上没有也不该有 {@code @TableId} ——
     * MyBatis-Plus 的单主键注解表达不了复合主键。
     *
     * <p>于是 {@code updateById} / {@code selectById} / {@code deleteById} 这几个
     * 基类方法对这个实体<b>根本生成不出来</b>，调了会在运行期抛
     * {@code Invalid bound statement (not found)}。
     *
     * <p>⚠️ 编译期一点问题都没有 —— 它们是 {@code BaseMapper} 上真实存在的方法。
     * 这个坑是 2026-09-15 真库跑通知链路时炸出来的。<b>本实体的任何按主键操作
     * 都要在这里显式写一个方法。</b>
     *
     * @return 影响行数，0 表示这一版不存在或本来就是停用的
     */
    int disableVersion(@Param("templateCode") String templateCode, @Param("version") Integer version);

    /** 删掉某一版。造数/测试收尾用 —— 生产上只停用不删除 */
    int deleteVersion(@Param("templateCode") String templateCode, @Param("version") Integer version);
}
