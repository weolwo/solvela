package sa.mall.favorite.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 商城-商品收藏 实体类
 *
 * @Author weolwo
 * @Date 2026-08-22 19:34:44
 * @Copyright weolwo
 */

@Data
@TableName("t_mall_favorite")
public class MallFavorite {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会员号：关联键
     */
    private Long memberId;

    /**
     * 商品id（商品粒度，不是SKU粒度）
     */
    private Long commodityId;

    /**
     * 收藏时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
