package net.lab1024.sa.base.module.support.file.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件。
 *
 * <p><b>文件名的三个身份，各有各的名字</b>（设计文档 §4.2）——
 * 这里刻意<b>没有任何一个字段叫 {@code fileName}</b>，因为那个名字本身没告诉你它是哪个，
 * 而旧实现的两个存储实现恰恰在这个字段上塞了相反的东西（一个塞生成名、一个塞原名），
 * 于是同一份代码在 local 和 cloud 下行为不同。
 *
 * <ul>
 *   <li>{@code storageKey} —— 系统生成、不可变，唯一寻址</li>
 *   <li>{@code originalName} —— 用户上传时带的原名，只用于展示和下载还原</li>
 *   <li>下载名（可被业务覆写）不落库，调用时传</li>
 * </ul>
 *
 * @Author 1024创新实验室: 罗伊
 * @Date 2019年10月11日 15:34:47
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@Data
@TableName(value = "t_file")
public class FileEntity {

    @TableId(type = IdType.AUTO)
    private Long fileId;

    /**
     * 分类，取代 folderType。
     */
    private Long categoryId;

    /**
     * @deprecated 被 {@link #categoryId} 取代。保留一个版本双写观察，档⑤ 随旧实现一并删除。
     */
    @Deprecated(since = "3.53.0")
    private Integer folderType;

    /**
     * 存储键，系统生成，<b>不可变</b>。换图是传新文件 + 改引用，绝不覆盖同一个 key（§7.6）。
     */
    private String storageKey;

    /**
     * 存储介质：LOCAL / S3。
     *
     * <p>不存它的后果是确定的：从 local 切到 cloud 之后，历史文件还在本地磁盘、新文件在 S3，
     * 而代码只能按当前全局配置去读 —— 切换那一刻所有历史文件立即失效。
     */
    private String storageKind;

    /**
     * 用户上传时的原始文件名。<b>只在入库和拼 Content-Disposition 两处出现</b>，中间环节一律不碰。
     */
    private String originalName;

    /**
     * 扩展名，<b>从嗅探出的 MIME 反推</b>，不从用户文件名取 —— 用户传 {@code evil.html}
     * 而内容是 PNG 时存成 {@code png}，这一条直接掐死存储型 XSS。
     */
    private String extension;

    /**
     * 嗅探出的真实 MIME。
     */
    private String contentType;

    /**
     * @deprecated 语义与 {@link #extension} 重复。保留一个版本，档⑤ 删除。
     */
    @Deprecated(since = "3.53.0")
    private String fileType;

    private Long fileSize;

    /**
     * SHA-256。去重预留，本版只存不用。
     */
    private String contentHash;

    /**
     * 图片宽高，非图片为 null。读取时用 {@code ImageReader.getWidth(0)} 只读文件头，
     * 不能用 {@code ImageIO.read()} —— 那会把整张图解码进堆。
     */
    private Integer imageWidth;

    private Integer imageHeight;

    /**
     * 可见性，见 {@code FileVisibilityEnum}。从路径前缀里解放出来的产物。
     */
    private Integer visibility;

    /**
     * 生命周期状态，见 {@code FileStatusEnum}。
     *
     * <p>没有它的话，"用户选了文件然后关掉页面没提交"产生的文件永远没人引用也永远不会被删，
     * 跑几年后存储里一半是垃圾，而且无法区分哪些是垃圾。
     */
    private Integer status;

    /**
     * 标签，<b>前后各带一个逗号</b>：{@code ,双十一,banner,}。
     * 查询必须用 {@code LIKE '%,618,%'}，否则搜「618」会命中「6180」。
     */
    private String tags;

    private Integer deletedFlag;

    /**
     * 创建人（用户名，即 {@code RequestUser#getUserName()} / {@code t_employee.actual_name}）。
     */
    private String createBy;

    private String updateBy;

    /**
     * @deprecated 被 {@link #createBy} 取代（语义完全一致，迁移时已平移）。档⑤ 删除。
     */
    @Deprecated(since = "3.53.0")
    private Long creatorId;

    @Deprecated(since = "3.53.0")
    private Integer creatorUserType;

    @Deprecated(since = "3.53.0")
    private String creatorName;

    private LocalDateTime updateTime;

    private LocalDateTime createTime;
}
