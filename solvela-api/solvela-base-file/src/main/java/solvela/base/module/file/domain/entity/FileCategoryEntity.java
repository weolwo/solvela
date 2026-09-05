package solvela.base.module.file.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件分类。取代硬编码的 {@code FileFolderTypeEnum}。
 *
 * <p><b>{@code categoryCode} 是本表最重要的字段</b>：代码必须引用 code（{@code "NOTICE"}），
 * <b>绝不能引用自增 ID</b> —— ID 由各环境数据库各自生成，dev 上「公告」是 2、prod 上可能是 7，
 * 而代码是同一份。这是"枚举变表"最经典的翻车点。
 *
 * <p><b>排序两条纪律</b>：
 * <ul>
 *   <li>查询必须 {@code ORDER BY sort ASC, category_id ASC}。第二排序键不能省 ——
 *       只按 sort 排，相同值时 MySQL 返回顺序不保证，表现是"每次刷新文件夹顺序都在变"</li>
 *   <li>{@code sort} 不加唯一索引 —— 拖拽的中间态必然有重复值</li>
 * </ul>
 *
 * <p>内置分类的删除保护<b>不在这张表上做</b>，见 {@code FileCategoryService#SYSTEM_CODES}：
 * 既然代码本来就按 code 引用，真相源留在代码侧比留在可被手工 UPDATE 掉的 DB 标记里更可靠。
 *
 * @Date 2026-08-10
 */
@Data
@TableName(value = "t_file_category")
public class FileCategoryEntity {

    @TableId(type = IdType.AUTO)
    private Long categoryId;

    /**
     * 稳定标识，代码引用它而非 ID。也是 storageKey 的第一段前缀。
     */
    private String categoryCode;

    /**
     * 显示名称，可随时改（不影响任何已存文件 —— 分类是元数据，不是路径）。
     */
    private String categoryName;

    private String categoryTag;

    private Integer sort;

    /**
     * 这个分类下的文件是否<b>免登录可读</b>（走 /file/public/**）。
     *
     * <p>🔴 <b>默认 false，公开要显式开口子。</b>反过来写的话，
     * 新建一个分类忘了设置就是默默裸奔，而那个方向的错误是数据泄露；
     * 设错成 false 的表现只是「图裂了」—— 看得见、改一下就好。
     * 与 C 端路由「默认需登录、公开页标 anonymous」是同一条取向。
     *
     * <p>为什么是 DB 列而不是像 {@code SYSTEM_CODES} 那样写死在代码里：
     * 那个名单回答的是「代码有没有硬编码引用这个分类」，只有代码知道；
     * 而「该不该免登录可见」是运营<b>新建分类时</b>的业务决定，
     * 代码不知道运营明天会建什么分类。放代码里等于每加一个分类都要发版。
     */
    private Boolean publicFlag;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
