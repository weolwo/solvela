package net.lab1024.sa.lottery.numberpool.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mysql.cj.jdbc.JdbcConnection;
import com.mysql.cj.jdbc.JdbcStatement;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.lottery.config.domain.entity.LotteryConfig;
import net.lab1024.sa.lottery.numberpool.dao.LotteryNumberPoolDao;
import net.lab1024.sa.lottery.numberpool.domain.entity.LotteryNumberPool;
import net.lab1024.sa.lottery.numberpool.domain.form.LotteryNumberPoolAddForm;
import net.lab1024.sa.lottery.numberpool.domain.form.LotteryNumberPoolQueryForm;
import net.lab1024.sa.lottery.numberpool.domain.form.LotteryNumberPoolUpdateForm;
import net.lab1024.sa.lottery.numberpool.domain.vo.LotteryNumberPoolVO;
import net.lab1024.sa.lottery.numberpool.manager.LotteryNumberPoolManager;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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
@Service
public class LotteryNumberPoolService {

    private final LotteryNumberPoolDao lotteryNumberPoolDao;
    private final LotteryNumberPoolManager lotteryNumberPoolManager;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 分页查询
     */
    public PageResult<LotteryNumberPoolVO> queryPage(LotteryNumberPoolQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<LotteryNumberPoolVO> list = lotteryNumberPoolDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(LotteryNumberPoolAddForm addForm) {
        LotteryNumberPool lotteryNumberPool = SmartBeanUtil.copy(addForm, LotteryNumberPool.class);
        lotteryNumberPoolDao.insert(lotteryNumberPool);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     */
    public ResponseDTO<String> update(LotteryNumberPoolUpdateForm updateForm) {
        LotteryNumberPool lotteryNumberPool = SmartBeanUtil.copy(updateForm, LotteryNumberPool.class);
        lotteryNumberPoolDao.updateById(lotteryNumberPool);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)) {
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
                    .append(i+1).append("\n");
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

    public List<LotteryNumberPool> queryNumbersBySeqNo(Integer minSeqNo,Integer maxSeqNo){
        return lotteryNumberPoolManager.lambdaQuery()
                .gt(LotteryNumberPool::getSequenceNo,minSeqNo)
                .le(LotteryNumberPool::getSequenceNo,maxSeqNo)
                .list();
    }
}
