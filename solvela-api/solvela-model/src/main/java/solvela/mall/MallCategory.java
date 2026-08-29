package solvela.mall;

import solvela.enums.EnableStatusEnum;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 商城-商品分类 实体类
 *
 * @Author weolwo
 * @Date 2026-08-22 19:28:16
 * @Copyright weolwo
 */

@Data
@TableName("t_mall_category")
public class MallCategory {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 父级id：0-顶级分类。业务上限死两级
     */
    private Long parentId;

    /**
     * 分类名称：如 数码3C / 虚拟权益
     */
    private String categoryName;

    /**
     * 分类图标 file_id（C端宫格导航用）
     */
    private Long iconFileId;

    /**
     * 排序：从小到大
     */
    private Integer sort;

    /**
     * 状态：0-禁用, 1-启用
     */
    private EnableStatusEnum status;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 创建时间
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
