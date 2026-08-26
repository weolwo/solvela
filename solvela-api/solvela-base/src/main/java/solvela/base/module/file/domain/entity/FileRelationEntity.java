package solvela.base.module.file.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件与业务对象的引用关系。取代业务表里「逗号分隔的 fileKey 字符串」。
 *
 * <p><b>逗号拼接为什么必须换掉</b>：那是「读 attachment → 拼上自己 → 写回」，
 * 典型的读改写竞态。单个上传时用户手动提交表单看不出问题，批量并发一来必丢，
 * 而且丢得很安静 —— 用户传了 5 个只看到 3 个。
 *
 * <p>换成关联表顺带解决三件事：并发追加安全、附件有顺序（轮播图必需）、
 * <b>能反查"这个文件被谁引用着"</b> —— 后者是安全删除的前提，逗号字符串永远做不到，
 * 所以现在这套系统实际上不敢删任何文件。
 *
 * <p><b>为什么这张表可以用通用的 {@code bizType}，而展示信息表不行</b>：
 * "文件被某个业务对象引用"这件事在所有业务里是<b>同构的</b>；而展示属性是<b>异构的</b>
 * （活动要规则、商品要参数、公告要落款）。同构的关系可以通用，异构的属性不要通用。
 *
 * @Date 2026-08-10
 */
@Data
@TableName(value = "t_file_relation")
public class FileRelationEntity {

    @TableId(type = IdType.AUTO)
    private Long relationId;

    private Long fileId;

    /**
     * 业务类型，如 {@code NOTICE} / {@code HELP_DOC} / {@code ACTIVITY_DISPLAY}。
     */
    private String bizType;

    private Long bizId;

    /**
     * 附件顺序。用户选了 10 张轮播图时顺序有业务含义，而 N 次并发上传的返回顺序是乱的。
     */
    private Integer sort;

    private LocalDateTime createTime;
}
