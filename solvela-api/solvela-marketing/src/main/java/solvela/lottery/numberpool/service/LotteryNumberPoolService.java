package solvela.lottery.numberpool.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mysql.cj.jdbc.JdbcStatement;
import lombok.RequiredArgsConstructor;
import solvela.base.common.domain.PageResult;
import solvela.base.common.domain.ResponseDTO;
import solvela.base.common.util.SolvelaBeanUtil;
import solvela.base.common.util.SolvelaCollectionUtil;
import solvela.base.common.util.SolvelaPageUtil;
import solvela.lottery.config.domain.entity.LotteryConfig;
import solvela.lottery.numberpool.dao.LotteryNumberPoolDao;
import solvela.lottery.numberpool.domain.entity.LotteryNumberPool;
import solvela.lottery.numberpool.domain.form.LotteryNumberPoolAddForm;
import solvela.lottery.numberpool.domain.form.LotteryNumberPoolQueryForm;
import solvela.lottery.numberpool.domain.form.LotteryNumberPoolUpdateForm;
import solvela.lottery.numberpool.domain.vo.LotteryNumberPoolVO;
import solvela.lottery.numberpool.manager.LotteryNumberPoolManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

/**
 * 彩票号码池 Service
 *
 * @Author weolwo
 * @Date 2026-04-19 11:31:09
 * @Copyright weolwo
 */
@RequiredArgsConstructor
public class LotteryNumberPoolService {

    private final LotteryNumberPoolDao lotteryNumberPoolDao;
    private final LotteryNumberPoolManager lotteryNumberPoolManager;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 分页查询
     */
    public PageResult<LotteryNumberPoolVO> queryPage(LotteryNumberPoolQueryForm queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<LotteryNumberPoolVO> list = lotteryNumberPoolDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(LotteryNumberPoolAddForm addForm) {
        LotteryNumberPool lotteryNumberPool = SolvelaBeanUtil.copy(addForm, LotteryNumberPool.class);
        lotteryNumberPoolDao.insert(lotteryNumberPool);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     */
    public ResponseDTO<String> update(LotteryNumberPoolUpdateForm updateForm) {
        LotteryNumberPool lotteryNumberPool = SolvelaBeanUtil.copy(updateForm, LotteryNumberPool.class);
        lotteryNumberPoolDao.updateById(lotteryNumberPool);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (SolvelaCollectionUtil.isEmpty(idList)) {
            return ResponseDTO.ok();
        }

        lotteryNumberPoolDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id) {
            return ResponseDTO.ok();
        }

        lotteryNumberPoolDao.deleteById(id);
        return ResponseDTO.ok();
    }

    public void fastLoadData(List<String> lotteryNumbers, LotteryConfig config) throws Exception {
        //每行约 25 个字符
        // lotteryCode(10) + "," + number(6) + "," + sequenceNo(6) + "\n" = 25
        int capacity = lotteryNumbers.size() * 25;
        StringBuilder csvData = new StringBuilder(capacity);

        for (int i = 0; i < lotteryNumbers.size(); i++) {
            csvData.append(config.getLotteryCode()).append(",")
                    .append(lotteryNumbers.get(i)).append(",")
                    .append(i + 1).append("\n");
        }

        InputStream inputStream = new ByteArrayInputStream(csvData.toString().getBytes(StandardCharsets.UTF_8));

        jdbcTemplate.execute((Connection conn) -> {
            String sql = "LOAD DATA LOCAL INFILE 'memory_stream' " +
                    "INTO TABLE t_lottery_number_pool " +
                    "FIELDS TERMINATED BY ',' " +
                    "LINES TERMINATED BY '\\n' " +
                    "(lottery_code, ticket_number, sequence_no)";

            // 1. 先用标准的 Connection 创建出 PreparedStatement
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

                //利用 unwrap 将被连接池代理的 Statement，剥离还原成 MySQL 专属的 JdbcStatement
                JdbcStatement mysqlStatement = pstmt.unwrap(JdbcStatement.class);

                //把内存流喂给这个 Statement！
                mysqlStatement.setLocalInfileInputStream(inputStream);
                pstmt.execute();
            }
            return null;
        });
    }

    public List<LotteryNumberPool> queryNumbersBySeqNo(String lotteryCode, Integer totalNum, int soldCount, int applyNum, int startOffset) {

        // 1. 计算真实的起始点（取模，防止溢出）
        int realMinSeq = (startOffset + soldCount) % totalNum == 0 ? totalNum : (startOffset + soldCount) % totalNum;
        int realMaxSeq = realMinSeq + applyNum;

        // 2. 判断是否触发了“跨界环绕”
        if (realMaxSeq <= totalNum) {
            // 【常规情况】：没有跨界，正常截取一段
            // 比如从 5000 取到 5005
            return lotteryNumberPoolManager.lambdaQuery()
                    .eq(LotteryNumberPool::getLotteryCode, lotteryCode)
                    .gt(LotteryNumberPool::getSequenceNo, realMinSeq)
                    .le(LotteryNumberPool::getSequenceNo, realMaxSeq)
                    .list();
        } else {
            // 【跨界情况】：游标走到池子尽头了，需要折返回开头取剩下的！
            // 比如池子一共 10 万，从 99998 开始取 5 个。
            // 第一段：尾部剩下的 (99998 ~ 100000] -> 取到 2 个
            // 第二段：折返到头部 (0 ~ 3] -> 取到 3 个

            int wrapCount = realMaxSeq - totalNum; // 折返后需要取的数量

            return lotteryNumberPoolManager.lambdaQuery()
                    .eq(LotteryNumberPool::getLotteryCode, lotteryCode)
                    .and(wrapper -> wrapper
                            // 条件1：取尾部
                            .gt(LotteryNumberPool::getSequenceNo, realMinSeq)
                            .or()
                            // 条件2：取头部
                            .le(LotteryNumberPool::getSequenceNo, wrapCount)
                    )
                    .list();
        }
    }
}
