package solvela.base.dao;

import solvela.base.util.SolvelaBeanUtil;
import solvela.base.util.SolvelaStringUtil;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.sql.SqlInjectionUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import solvela.base.domain.PageParam;
import solvela.base.domain.PageResult;
import solvela.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;

import solvela.base.util.SolvelaCollectionUtil;

/**
 * 分页工具类
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2020-04-23 20:51:40
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
@Slf4j
public class SolvelaPageUtil {

    /**
     * 转换为查询参数
     */
    public static Page<?> convert2PageQuery(PageParam pageParam) {
        Page<?> page = new Page<>(pageParam.getPageNum(), pageParam.getPageSize());

        if (pageParam.getSearchCount() != null) {
            page.setSearchCount(pageParam.getSearchCount());
        }

        List<PageParam.SortItem> sortItemList = pageParam.getSortItemList();
        if (SolvelaCollectionUtil.isEmpty(sortItemList)) {
            return page;
        }

        // 设置排序字段并检测是否含有sql注入
        List<OrderItem> orderItemList = new ArrayList<>();
        for (PageParam.SortItem sortItem : sortItemList) {

            if (SolvelaStringUtil.isEmpty(sortItem.getColumn())) {
                continue;
            }

            if (SqlInjectionUtils.check(sortItem.getColumn())) {
                log.error("《存在SQL注入：》 : {}", sortItem.getColumn());
                throw new BusinessException("存在SQL注入风险，请联系技术工作人员！");
            }
            orderItemList.add(sortItem.getIsAsc() ? OrderItem.asc(sortItem.getColumn()) : OrderItem.desc(sortItem.getColumn()));
        }
        page.setOrders(orderItemList);
        return page;
    }

    /**
     * 转换为 PageResult 对象
     */
    public static <T, E> PageResult<T> convert2PageResult(Page<?> page, List<E> sourceList, Class<T> targetClazz) {
        return convert2PageResult(page, SolvelaBeanUtil.copyList(sourceList, targetClazz));
    }

    /**
     * 转换为 PageResult 对象
     */
    public static <E> PageResult<E> convert2PageResult(Page<?> page, List<E> sourceList) {
        return new PageResult<>(page.getCurrent(), page.getSize(), page.getTotal(), page.getPages(), sourceList);
    }

    /**
     * 转换分页结果对象：只换元素类型，分页元信息原样带过去
     */
    public static <E, T> PageResult<T> convert2PageResult(PageResult<E> pageResult, Class<T> targetClazz) {
        return new PageResult<>(pageResult.pageNum(), pageResult.pageSize(), pageResult.total(), pageResult.pages(),
                SolvelaBeanUtil.copyList(pageResult.list(), targetClazz));
    }

    /**
     * 内存分页：整份数据已经在手上，只取其中一页
     */
    public static <T> PageResult<T> subListPage(Integer pageNum, Integer pageSize, List<T> list) {
        int count = list.size();
        int pages = count % pageSize == 0 ? count / pageSize : (count / pageSize + 1);
        if (pageNum > pages) {
            return new PageResult<>(pageNum, pageSize, count, pages, List.of());
        }
        int fromIndex = (pageNum - 1) * pageSize;
        int toIndex = Math.min(pageNum * pageSize, count);
        return new PageResult<>(pageNum, pageSize, count, pages, List.copyOf(list.subList(fromIndex, toIndex)));
    }
}
