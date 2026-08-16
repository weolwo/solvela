package sa.base.module.support.file.dao;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import sa.base.module.support.file.domain.entity.FileCategoryEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 文件分类。
 *
 * @Date 2026-08-10
 */
@Mapper
public interface FileCategoryDao extends BaseMapper<FileCategoryEntity> {

    /**
     * 按 code 取。<b>业务代码只应该用这个方法定位分类，不要用 ID</b> ——
     * ID 各环境各自生成，code 才是跨环境稳定的。
     */
    default FileCategoryEntity getByCode(String categoryCode) {
        return selectOne(new LambdaQueryWrapper<FileCategoryEntity>()
                .eq(FileCategoryEntity::getCategoryCode, categoryCode));
    }

    /**
     * 全部分类，按展示顺序。
     *
     * <p><b>第二排序键 categoryId 不能省</b>：只按 sort 排，相同值时 MySQL 返回顺序不保证，
     * 表现是"每次刷新页面文件夹顺序都在变"，很难查。
     */
    default List<FileCategoryEntity> listOrdered() {
        return selectList(new LambdaQueryWrapper<FileCategoryEntity>()
                .orderByAsc(FileCategoryEntity::getSort)
                .orderByAsc(FileCategoryEntity::getCategoryId));
    }
}
