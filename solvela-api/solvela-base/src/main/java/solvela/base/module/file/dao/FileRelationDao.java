package solvela.base.module.file.dao;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import solvela.base.module.file.domain.entity.FileRelationEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * 文件业务引用。
 *
 * @Date 2026-08-10
 */
@Mapper
public interface FileRelationDao extends BaseMapper<FileRelationEntity> {

    /**
     * 某个业务对象引用了哪些文件，按 sort 排。
     */
    default List<FileRelationEntity> listByBiz(String bizType, Long bizId) {
        return selectList(new LambdaQueryWrapper<FileRelationEntity>()
                .eq(FileRelationEntity::getBizType, bizType)
                .eq(FileRelationEntity::getBizId, bizId)
                .orderByAsc(FileRelationEntity::getSort)
                .orderByAsc(FileRelationEntity::getRelationId));
    }

    /**
     * 这些文件还有没有人在引用。<b>物理删除前必须查这个</b>，走 idx_file 索引。
     */
    default List<FileRelationEntity> listByFileIds(Collection<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<FileRelationEntity>()
                .in(FileRelationEntity::getFileId, fileIds));
    }

    /**
     * 解除某个业务对象的全部引用（业务对象被删除时调用）。
     *
     * <p>只删关系不删文件 —— 文件可能被别处引用着，是否物理删除由引用计数说了算。
     */
    default int deleteByBiz(String bizType, Long bizId) {
        return delete(new LambdaQueryWrapper<FileRelationEntity>()
                .eq(FileRelationEntity::getBizType, bizType)
                .eq(FileRelationEntity::getBizId, bizId));
    }
}
