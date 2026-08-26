package solvela.lottery.numberpool.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import solvela.base.dao.CustomizedBaseMapper;
import solvela.lottery.LotteryNumberPool;
import solvela.lottery.numberpool.domain.form.LotteryNumberPoolQueryForm;
import solvela.lottery.numberpool.domain.vo.LotteryNumberPoolVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 彩票号码池 Dao
 *
 * @Author weolwo
 * @Date 2026-04-19 11:31:09
 * @Copyright weolwo
 */
public interface LotteryNumberPoolDao extends CustomizedBaseMapper<LotteryNumberPool> {

    /**
     * 分页查询
     *
     * @param page      分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<LotteryNumberPoolVO> queryPage(Page<?> page, @Param("queryForm") LotteryNumberPoolQueryForm queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<LotteryNumberPoolVO> queryList(@Param("queryForm") LotteryNumberPoolQueryForm queryForm);

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