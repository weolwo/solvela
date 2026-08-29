package solvela.mall.category.service;

import solvela.enums.EnableStatusEnum;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solvela.base.domain.PageResult;
import solvela.exception.BusinessException;
import solvela.base.dao.SolvelaPageUtil;
import solvela.mall.category.dao.MallCategoryDao;
import solvela.mall.MallCategory;
import solvela.mall.category.domain.command.MallCategoryBatchItemCommand;
import solvela.mall.category.domain.command.MallCategoryBatchSaveCommand;
import solvela.mall.category.domain.query.MallCategoryQuery;
import solvela.mall.category.domain.command.MallCategorySaveCommand;
import solvela.mall.category.domain.dto.MallCategoryDTO;
import solvela.mall.category.manager.MallCategoryManager;
import solvela.mall.MallCommodity;
import solvela.mall.commodity.manager.MallCommodityManager;
import solvela.mall.constant.MallConst;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商城-商品分类 Service
 *
 * <p>分类刻意<b>没有 category_code</b>：它是纯运营数据，代码不引用它（素材分类那条
 * 「一律按 categoryCode 引用」的教训针对的是被代码硬编码引用的分类，这里不适用）。
 * C 端宫格导航按 id 取即可。
 *
 * @Author weolwo
 * @Date 2026-08-22 19:28:16
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MallCategoryService {

    private final MallCategoryDao mallCategoryDao;
    private final MallCategoryManager mallCategoryManager;
    /** 删除守卫要看这个分类下还有没有商品 */
    private final MallCommodityManager mallCommodityManager;

    /** 顶级分类的 parent_id */
    private static final long ROOT_PARENT_ID = 0L;

    /**
     * 分页查询
     */
    public PageResult<MallCategoryDTO> queryPage(MallCategoryQuery queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<MallCategoryDTO> list = mallCategoryDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }

    /**
     * 全部分类（不分页），按 sort 排。管理端列表页用它自己拼树。
     *
     * <p>不分页是有意的：两级、量级在几十，分页反而会把「父在第 2 页、子在第 1 页」这种
     * 拼不出树的情况带进来。
     */
    public List<MallCategoryDTO> queryAll() {
        MallCategoryQuery queryForm = new MallCategoryQuery();
        return mallCategoryDao.queryList(queryForm);
    }

    /**
     * 启用中的分类，给商品编辑页的分类下拉用。
     *
     * <p><b>父级被停用的子分类也要排除掉</b>。只按 status=1 过滤的话，运营停用了「数码3C」
     * 之后，它下面的「手机配件」还留在下拉里 —— 而那是一个挂在不可见父级下的孤儿，
     * C 端根本走不到，商品配上去就等于配了个看不见的分类。
     */
    public List<MallCategoryDTO> queryEnabledList() {
        MallCategoryQuery queryForm = new MallCategoryQuery();
        queryForm.setStatus(EnableStatusEnum.ENABLED);
        List<MallCategoryDTO> enabled = mallCategoryDao.queryList(queryForm);

        Set<Long> enabledRootIds = enabled.stream()
                .filter(c -> isRoot(c.getParentId()))
                .map(MallCategoryDTO::getId)
                .collect(Collectors.toSet());
        List<MallCategoryDTO> result = enabled.stream()
                .filter(c -> isRoot(c.getParentId()) || enabledRootIds.contains(c.getParentId()))
                .collect(Collectors.toList());
        return result;
    }

    private static boolean isRoot(Long parentId) {
        return parentId == null || parentId == ROOT_PARENT_ID;
    }

    /**
     * 保存（id 为空即新建）。
     *
     * <p>三条约束都在这里卡，一条都不能只靠数据库：
     * <ol>
     *   <li><b>最多两级</b> —— 唯一索引管不了层级，只能在这里算</li>
     *   <li><b>同级不许重名</b> —— 有唯一索引兜底，但让它抛 SQL 异常，运营看到的是英文堆栈</li>
     *   <li><b>不能把自己挂到自己下面</b> —— 会造出一个查询时永远出不来的环</li>
     * </ol>
     */
    @Transactional(rollbackFor = Exception.class)
    public Long save(MallCategorySaveCommand form, String operator) {
        long parentId = form.getParentId() == null ? ROOT_PARENT_ID : form.getParentId();
        String categoryName = StringUtils.trim(form.getCategoryName());

        MallCategory existing = form.getId() == null ? null : mallCategoryManager.getById(form.getId());
        if (form.getId() != null && existing == null) {
            throw new BusinessException("分类不存在，可能已被删除");
        }

        // ---------- 层级约束 ----------
        if (!isRoot(parentId)) {
            MallCategory parent = mallCategoryManager.getById(parentId);
            if (parent == null) {
                throw new BusinessException("上级分类不存在");
            }
            if (!isRoot(parent.getParentId())) {
                throw new BusinessException("分类最多两级，不能挂在二级分类下面");
            }
            if (existing != null) {
                if (parentId == existing.getId()) {
                    throw new BusinessException("不能把分类挂到它自己下面");
                }
                // 自己有子分类，却要变成二级 —— 那些子分类就成了三级
                long childCount = mallCategoryManager.lambdaQuery()
                        .eq(MallCategory::getParentId, existing.getId()).count();
                if (childCount > 0) {
                    throw new BusinessException("该分类下还有 " + childCount + " 个子分类，不能改成二级分类");
                }
            }
        }

        // ---------- 同级重名 ----------
        boolean nameTaken = mallCategoryManager.lambdaQuery()
                .eq(MallCategory::getParentId, parentId)
                .eq(MallCategory::getCategoryName, categoryName)
                .ne(existing != null, MallCategory::getId, existing == null ? null : existing.getId())
                .exists();
        if (nameTaken) {
            // 不加这一条的话，运营手滑建两个「数码3C」，C 端会出现两个一模一样的宫格
            throw new BusinessException("同级下已有名为「" + categoryName + "」的分类");
        }

        MallCategory entity = new MallCategory();
        entity.setId(form.getId());
        entity.setParentId(parentId);
        entity.setCategoryName(categoryName);
        entity.setIconFileId(form.getIconFileId());
        entity.setSort(form.getSort() == null ? 0 : form.getSort());
        entity.setStatus(form.getStatus() == null ? EnableStatusEnum.ENABLED : form.getStatus());
        // create_time / update_time 一律不设：铁律 9，只认数据库时钟
        if (existing == null) {
            entity.setCreateBy(operator);
            entity.setUpdateBy(operator);
            mallCategoryDao.insert(entity);
        } else {
            entity.setUpdateBy(operator);
            mallCategoryDao.updateById(entity);
        }
        return entity.getId();
    }

    /**
     * 批量新建：一次建多个，可带一层子分类，同一个事务。
     *
     * <p>子分类的 parentId 依赖父分类的自增 id —— 父分类此刻还没入库，前端填不出这个值。
     * 所以结构上让子分类嵌在父节点里，由这里「先建父、拿 id、再建子」。
     * 这是批量新建能同时录上下级的<b>全部理由</b>；平铺 + 让前端填 parentId 是做不到的。
     *
     * @return 实际创建的分类数（父 + 子）
     */
    @Transactional(rollbackFor = Exception.class)
    public Integer batchSave(MallCategoryBatchSaveCommand form, String operator) {
        long parentId = form.getParentId() == null ? ROOT_PARENT_ID : form.getParentId();
        List<MallCategoryBatchItemCommand> itemList = form.getCategoryList();

        // ---------- 上级校验 ----------
        boolean underRoot = isRoot(parentId);
        if (!underRoot) {
            MallCategory parent = mallCategoryManager.getById(parentId);
            if (parent == null) {
                throw new BusinessException("上级分类不存在");
            }
            if (!isRoot(parent.getParentId())) {
                throw new BusinessException("分类最多两级，不能挂在二级分类下面");
            }
        }

        // ---------- 层级与数量 ----------
        int total = 0;
        for (MallCategoryBatchItemCommand item : itemList) {
            List<MallCategoryBatchItemCommand> children = childrenOf(item);
            if (!underRoot && !children.isEmpty()) {
                // 本批已经挂在一级分类下了，再带子级就是第三级
                throw new BusinessException("分类最多两级：「" + item.getCategoryName() + "」不能再有子分类");
            }
            for (MallCategoryBatchItemCommand child : children) {
                if (!childrenOf(child).isEmpty()) {
                    throw new BusinessException("分类最多两级：「" + child.getCategoryName() + "」不能再有子分类");
                }
            }
            total += 1 + children.size();
        }
        if (total > MallConst.MAX_CATEGORY_BATCH) {
            throw new BusinessException("一次最多新建 " + MallConst.MAX_CATEGORY_BATCH
                    + " 个分类，当前 " + total + " 个，请分批提交");
        }

        // ---------- 重名：本批内部 + 与库里已有 ----------
        //
        // 两处都要查。只查库里的话，本批里写了两个「数码3C」会一路走到 insert，
        // 由唯一索引在第二条上抛 SQL 异常 —— 事务是回滚了，但运营看到的是英文堆栈，
        // 而且不知道是哪两行撞了。
        Set<String> existingNames = listChildNames(parentId);
        Set<String> batchNames = new HashSet<>();
        for (MallCategoryBatchItemCommand item : itemList) {
            String name = StringUtils.trim(item.getCategoryName());
            if (existingNames.contains(name)) {
                throw new BusinessException("同级下已有名为「" + name + "」的分类");
            }
            if (!batchNames.add(name)) {
                throw new BusinessException("本次提交里有两个同级的「" + name + "」");
            }
            // 子分类的父是本次新建的，库里不可能已有同名的兄弟，只需查本批内部
            Set<String> childNames = new HashSet<>();
            for (MallCategoryBatchItemCommand child : childrenOf(item)) {
                String childName = StringUtils.trim(child.getCategoryName());
                if (!childNames.add(childName)) {
                    throw new BusinessException("「" + name + "」下有两个同名的子分类「" + childName + "」");
                }
            }
        }

        // ---------- 落库：先父后子 ----------
        int created = 0;
        for (int i = 0; i < itemList.size(); i++) {
            MallCategoryBatchItemCommand item = itemList.get(i);
            MallCategory parentEntity = toEntity(item, parentId, i, operator);
            mallCategoryDao.insert(parentEntity);
            created++;

            List<MallCategoryBatchItemCommand> children = childrenOf(item);
            for (int j = 0; j < children.size(); j++) {
                // 这里才拿得到父的自增 id —— 整个嵌套结构就是为了这一行
                mallCategoryDao.insert(toEntity(children.get(j), parentEntity.getId(), j, operator));
                created++;
            }
        }
        return created;
    }

    private static List<MallCategoryBatchItemCommand> childrenOf(MallCategoryBatchItemCommand item) {
        return item.getChildren() == null ? List.of() : item.getChildren();
    }

    /** 某个上级下已有的分类名，用于重名判断 */
    private Set<String> listChildNames(long parentId) {
        return mallCategoryManager.lambdaQuery()
                .eq(MallCategory::getParentId, parentId)
                .list().stream()
                .map(MallCategory::getCategoryName)
                .collect(Collectors.toSet());
    }

    private MallCategory toEntity(MallCategoryBatchItemCommand item, Long parentId, int index, String operator) {
        MallCategory entity = new MallCategory();
        entity.setParentId(parentId);
        entity.setCategoryName(StringUtils.trim(item.getCategoryName()));
        entity.setIconFileId(item.getIconFileId());
        // 没填排序就按录入顺序来 —— 运营在表单里从上往下写的顺序，就是他想要的展示顺序
        entity.setSort(item.getSort() == null ? index : item.getSort());
        entity.setStatus(item.getStatus() == null ? EnableStatusEnum.ENABLED : item.getStatus());
        entity.setCreateBy(operator);
        entity.setUpdateBy(operator);
        // create_time / update_time 一律不设：铁律 9，只认数据库时钟
        return entity;
    }

    /**
     * 启用 / 停用。
     *
     * <p>停用一个顶级分类会连带让它下面的子分类在 C 端不可见（{@link #queryEnabledList}
     * 会把它们排除）。列表页在点停用前会按树里的子节点数提示运营波及多大。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, EnableStatusEnum status, String operator) {
        if (status == null) {
            throw new BusinessException("分类状态不合法");
        }
        MallCategory category = mallCategoryManager.getById(id);
        if (category == null) {
            throw new BusinessException("分类不存在");
        }
        // 子分类要启用，父级得先是启用的，否则它启用了也没人看得见
        if (status == EnableStatusEnum.ENABLED && !isRoot(category.getParentId())) {
            MallCategory parent = mallCategoryManager.getById(category.getParentId());
            if (parent != null && parent.getStatus() == EnableStatusEnum.DISABLED) {
                throw new BusinessException("上级分类「" + parent.getCategoryName() + "」已停用，请先启用它");
            }
        }
        MallCategory update = new MallCategory();
        update.setId(id);
        update.setStatus(status);
        update.setUpdateBy(operator);
        mallCategoryDao.updateById(update);
    }

    /**
     * 批量删除。有一个删不掉就整批停下 —— 「删了 3 个、第 4 个失败」运营不知道该怎么收拾。
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<Long> idList) {
        if (idList == null || idList.isEmpty()) {
            return;
        }
        // delete 自己抛 BusinessException，异常穿透本方法即触发回滚，
        // 「要么全删、要么一个没删」由事务保证，这里不需要再判返回值
        idList.forEach(this::delete);
    }

    /**
     * 单个删除。
     *
     * <p>两道守卫，删掉任意一道都会留下指向空气的数据：
     * <ul>
     *   <li>下面还有子分类 —— 删了父级，子分类的 parent_id 就指向一个不存在的行，
     *       它从此不会出现在任何一棵树里，但记录还在，运营只会觉得「分类莫名其妙不见了」</li>
     *   <li>下面还有商品 —— 商品的 category_id 会指向空，C 端分类页取不到它，
     *       后台列表的分类列变空白</li>
     * </ul>
     * 「不想要了」的正确动作是停用，不是删除。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (id == null) {
            return;
        }
        MallCategory category = mallCategoryManager.getById(id);
        if (category == null) {
            return;
        }
        long childCount = mallCategoryManager.lambdaQuery().eq(MallCategory::getParentId, id).count();
        if (childCount > 0) {
            throw new BusinessException("分类「" + category.getCategoryName() + "」下还有 "
                    + childCount + " 个子分类，请先处理它们");
        }
        long commodityCount = mallCommodityManager.lambdaQuery().eq(MallCommodity::getCategoryId, id).count();
        if (commodityCount > 0) {
            throw new BusinessException("分类「" + category.getCategoryName() + "」下还有 "
                    + commodityCount + " 个商品，请先把它们移到别的分类；不想用了可以改为停用");
        }
        mallCategoryDao.deleteById(id);
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
