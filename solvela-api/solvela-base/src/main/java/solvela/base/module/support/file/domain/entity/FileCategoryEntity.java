package solvela.base.module.support.file.domain.entity;

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

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
