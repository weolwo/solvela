package net.lab1024.sa.activity.dao;

        import java.util.List;
        import net.lab1024.sa.activity.domain.entity.ActivityConfig;
        import net.lab1024.sa.activity.domain.form.ActivityConfigQueryForm;
        import net.lab1024.sa.activity.domain.vo.ActivityConfigVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
        import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 活动配置 Dao
 *
 * @Author weolwo
 * @Date 2026-04-18 19:31:49
 * @Copyright weolwo
 */
@Mapper
public interface ActivityConfigDao extends BaseMapper<ActivityConfig> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<ActivityConfigVO> queryPage(Page<?> page, @Param("queryForm") ActivityConfigQueryForm queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<ActivityConfigVO> queryList(@Param("queryForm") ActivityConfigQueryForm queryForm);

            // ----- 物理删除 -----
                /**
                 * 单个物理删除
                 */
                long deleteById(@Param("id") Long id);

                /**
                 * 批量物理删除
                 */
                void batchDelete(@Param("idList") List<Long> idList);
}