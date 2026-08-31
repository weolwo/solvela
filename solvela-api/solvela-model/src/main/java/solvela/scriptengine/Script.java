package solvela.scriptengine;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import solvela.enums.ScriptSourceEnum;

import java.time.LocalDateTime;

/**
 * 脚本表 实体类。<b>一行 = 一个版本</b>，不是一个脚本。
 *
 * <p>同一个 {@code script_code} 有 N 行，其中<b>至多一行</b> {@code active_flag = 1}，
 * 那一行就是运行期真正会被执行的内容。发布与回滚是同一个动作：把 active 标记挪到另一行。
 *
 * <h3>两套版本管理，各管各的</h3>
 * <ul>
 *   <li><b>git</b>：{@code resources/scripts/} 是开发的工作区，管的是「怎么演化到今天这样」——
 *       diff、review、blame 都在那边；</li>
 *   <li><b>本表</b>：管的是「线上此刻跑的是哪一版、出事了退回哪一版」。</li>
 * </ul>
 * 🔴 <b>运行期只认本表</b>。启动时不再扫描文件覆盖写库 —— 那会让「谁是权威」取决于部署时机，
 * 后台的改动会在下次发版时被静默覆盖。
 *
 * @see solvela.enums.ScriptSourceEnum
 */
@Data
@TableName("t_script")
public class Script {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 脚本唯一编码，如 task/streak_sign_7d。同一编码下有多行，靠 version 区分
     */
    private String scriptCode;

    /**
     * 脚本名称
     */
    private String scriptName;

    /**
     * 业务域，对应 ScriptDomain 枚举，由 scene 推导
     */
    private String domain;

    /**
     * 场景，对应 ScriptScene 枚举。决定入参与返回值契约
     */
    private String scene;

    /**
     * 来源文件路径。仅 {@code source = FILE} 的行有值，后台录入的为 null
     */
    private String filePath;

    /**
     * 脚本内容。<b>这一行落库后就不再改</b>，改脚本 = 新增一行
     */
    private String content;

    /**
     * content 的 SHA-256。内容没变时不产生新版本，靠它判断
     */
    private String contentHash;

    /**
     * 版本号，同一 script_code 下从 1 递增。与 script_code 组成唯一键
     */
    private Integer version;

    /**
     * 激活标记。{@code TRUE} = 当前生效的版本；历史版本一律 {@code null}。
     *
     * <p>🔴 <b>停用时必须写 null，绝不能写 false。</b>唯一键 {@code (script_code, active_flag)}
     * 靠「NULL 在唯一索引里不参与判重」来保证「至多一个激活版本」；
     * 写 false 的话两行历史版本就成了重复键，同一个脚本将只能存下<b>一个</b>历史版本。
     *
     * <p>⚠️ MyBatis-Plus 的 {@code updateById} 默认跳过 null 字段，
     * 所以「把旧版本置为未激活」必须用 {@code UpdateWrapper.set(...)} 显式写 null，
     * 否则那条 update 会静默不生效 —— 结果是两行同时 active，而唯一键会当场拦下来。
     */
    private Boolean activeFlag;

    /**
     * 这一版是怎么进来的，见 {@link ScriptSourceEnum}。只供排查，不参与生效判断
     */
    private ScriptSourceEnum source;

    /**
     * 入参契约快照 JSON，由 ScriptScene.getParams() 生成
     */
    private String paramsSchema;

    /**
     * 返回值类型
     */
    private String returnType;

    /**
     * 用途说明
     */
    private String description;

    /**
     * 这一版改了什么。后台录入时填，是本表相对 git 唯一能自带的「改动理由」
     */
    private String changeLog;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
