package net.lab1024.sa.base.module.support.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import net.lab1024.sa.base.common.domain.RequestUser;
import net.lab1024.sa.base.common.exception.BusinessException;
import net.lab1024.sa.base.module.support.file.dao.FileCategoryDao;
import net.lab1024.sa.base.module.support.file.dao.FileDao;
import net.lab1024.sa.base.module.support.file.domain.entity.FileCategoryEntity;
import net.lab1024.sa.base.module.support.file.domain.entity.FileEntity;
import net.lab1024.sa.base.module.support.file.domain.vo.FileCategoryVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文件分类的后台管理。
 *
 * @Date 2026-08-10
 */
@Service
public class FileCategoryService {

    /**
     * 内置分类：<b>代码按 code 引用它们</b>（公告附件必须进 NOTICE 分类），
     * 运营在后台删掉的话，历史文件成孤儿、代码直接抛异常。
     *
     * <p><b>刻意放在代码里而不是加一个 {@code system_flag} 列</b>：既然引用关系本来就在代码侧，
     * 真相源留在这里比留在一个可能被手工 UPDATE 掉的 DB 标记里更可靠，而且零列开销。
     */
    public static final Set<String> SYSTEM_CODES = Set.of("COMMON", "NOTICE", "HELP_DOC", "FEEDBACK");

    @Resource
    private FileCategoryDao fileCategoryDao;

    @Resource
    private FileDao fileDao;

    /**
     * 全部分类，按展示顺序。
     */
    public List<FileCategoryEntity> list() {
        return fileCategoryDao.listOrdered();
    }

    /**
     * 带文件数的分类列表，给素材库的分类卡片用。
     *
     * <p><b>两次查询搞定，不是每张卡片查一次</b>：分类列表一次 + GROUP BY 计数一次。
     * 逐个 count 是 N+1，而这是运营每天进的页面。
     */
    public List<FileCategoryVO> listWithCount() {
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : fileDao.countGroupByCategory()) {
            Object id = row.get("categoryId");
            Object count = row.get("fileCount");
            if (id instanceof Number idNum && count instanceof Number countNum) {
                counts.put(idNum.longValue(), countNum.longValue());
            }
        }
        return fileCategoryDao.listOrdered().stream().map(entity -> {
            FileCategoryVO vo = new FileCategoryVO();
            vo.setCategoryId(entity.getCategoryId());
            vo.setCategoryCode(entity.getCategoryCode());
            vo.setCategoryName(entity.getCategoryName());
            vo.setCategoryTag(entity.getCategoryTag());
            vo.setSort(entity.getSort());
            vo.setFileCount(counts.getOrDefault(entity.getCategoryId(), 0L));
            // 前端据此把内置分类的删除按钮灰掉，而不是等点了之后收一个报错
            vo.setSystemFlag(SYSTEM_CODES.contains(entity.getCategoryCode()));
            return vo;
        }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public FileCategoryEntity add(FileCategoryEntity form, RequestUser user) {
        String code = requireCode(form.getCategoryCode());
        if (fileCategoryDao.getByCode(code) != null) {
            throw new BusinessException("分类编码已存在：" + code);
        }
        form.setCategoryCode(code);
        form.setCategoryId(null);
        form.setSort(form.getSort() == null ? nextSort() : form.getSort());
        form.setCreateBy(user == null ? null : user.getUserName());
        form.setUpdateBy(form.getCreateBy());
        fileCategoryDao.insert(form);
        return form;
    }

    /**
     * 更新。<b>内置分类的 code 不许改</b> —— 改了等于把代码里的引用全部指空，
     * 而这个后果要等到下一次上传公告附件时才会暴露。名称和标签随便改，那不影响任何引用。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(FileCategoryEntity form, RequestUser user) {
        FileCategoryEntity existing = require(form.getCategoryId());
        String newCode = requireCode(form.getCategoryCode());
        if (!existing.getCategoryCode().equals(newCode)) {
            if (SYSTEM_CODES.contains(existing.getCategoryCode())) {
                throw new BusinessException("内置分类的编码不允许修改：" + existing.getCategoryCode());
            }
            if (fileCategoryDao.getByCode(newCode) != null) {
                throw new BusinessException("分类编码已存在：" + newCode);
            }
        }
        form.setCategoryCode(newCode);
        form.setUpdateBy(user == null ? null : user.getUserName());
        fileCategoryDao.updateById(form);
    }

    /**
     * 删除。两道闸：内置分类不许删；分类下还有文件不许删。
     *
     * <p>第二道不是洁癖 —— 分类没了之后那些文件的 {@code category_id} 就指向不存在的行，
     * 列表页查不出来、清理任务也不知道该怎么处置，等于凭空制造一批幽灵文件。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long categoryId) {
        FileCategoryEntity existing = require(categoryId);
        if (SYSTEM_CODES.contains(existing.getCategoryCode())) {
            throw new BusinessException("内置分类不允许删除：" + existing.getCategoryCode());
        }
        Long count = fileDao.selectCount(new LambdaQueryWrapper<FileEntity>()
                .eq(FileEntity::getCategoryId, categoryId)
                .eq(FileEntity::getDeletedFlag, 0));
        if (count != null && count > 0) {
            throw new BusinessException("该分类下还有 " + count + " 个文件，不能删除");
        }
        fileCategoryDao.deleteById(categoryId);
    }

    /**
     * 拖拽排序：按给定的 ID 顺序重排。
     *
     * <p>分类量级只有几十个，<b>全量重排就够了</b>，不需要稀疏整数或 LexoRank ——
     * 那是几百上千个分类才需要的方案，现在上是过度设计。
     *
     * <p>整个重排必须在一个事务里：中途失败会留下一半新序一半旧序，
     * 而排序值没有唯一约束（拖拽中间态必然有重复），这种半成品状态肉眼看不出来。
     */
    @Transactional(rollbackFor = Exception.class)
    public void reorder(List<Long> orderedIds, RequestUser user) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            return;
        }
        String operator = user == null ? null : user.getUserName();
        for (int i = 0; i < orderedIds.size(); i++) {
            FileCategoryEntity update = new FileCategoryEntity();
            update.setCategoryId(orderedIds.get(i));
            update.setSort(i + 1);
            update.setUpdateBy(operator);
            fileCategoryDao.updateById(update);
        }
    }

    // ------------------------------------------------------------------

    private FileCategoryEntity require(Long categoryId) {
        FileCategoryEntity existing = categoryId == null ? null : fileCategoryDao.selectById(categoryId);
        if (existing == null) {
            throw new BusinessException("分类不存在");
        }
        return existing;
    }

    /**
     * code 会成为 storageKey 的第一段前缀，字符集必须收窄 ——
     * 允许中文或斜杠就等于让运营从后台决定对象存储的目录结构。
     */
    private static String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("分类编码不能为空");
        }
        String trimmed = code.trim();
        if (!trimmed.matches("[A-Za-z0-9_-]{1,50}")) {
            throw new BusinessException("分类编码只允许字母、数字、下划线和短横线，且不超过 50 位");
        }
        return trimmed;
    }

    private int nextSort() {
        List<FileCategoryEntity> all = fileCategoryDao.listOrdered();
        return all.isEmpty() ? 1 : all.getLast().getSort() + 1;
    }
}
