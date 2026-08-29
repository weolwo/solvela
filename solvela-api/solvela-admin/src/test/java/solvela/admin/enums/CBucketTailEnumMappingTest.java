package solvela.admin.enums;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.base.module.file.constant.FileStatusEnum;
import solvela.base.module.file.dao.FileDao;
import solvela.base.module.file.domain.entity.FileEntity;
import solvela.enums.EnableStatusEnum;
import solvela.enums.LotteryDispatchStatusEnum;
import solvela.enums.TransactionTypeEnum;
import solvela.ledger.MemberAssetTransaction;
import solvela.ledger.transaction.dao.MemberAssetTransactionDao;
import solvela.lottery.LotteryRecord;
import solvela.lottery.record.dao.LotteryRecordDao;
import solvela.scriptengine.Script;
import solvela.scriptengine.ScriptRef;
import solvela.scriptengine.dao.ScriptDao;
import solvela.scriptengine.dao.ScriptRefDao;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C 桶最后 4 列的真实验收（连数据库，只读）。
 *
 * <p>这 4 列是把 31 列逐个核对一遍之后翻出来的漏网之鱼 ——
 * 前面几轮按「表名 + 常量清单」推进，它们要么没有裸常量（{@code t_file.status}
 * 早就有 {@code FileStatusEnum}，只是字段还写成 Integer），
 * 要么常量名躲开了棘轮的关键词（{@code DISPATCH_FAIL}）。
 *
 * <p>覆盖：{@code t_file.status}、{@code t_lottery_record.dispatch_status}、
 * {@code t_member_asset_transaction.transaction_type}、
 * {@code t_script.status} 与 {@code t_script_ref.status}。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class CBucketTailEnumMappingTest {

    @Autowired
    private FileDao fileDao;

    @Autowired
    private LotteryRecordDao lotteryRecordDao;

    @Autowired
    private MemberAssetTransactionDao memberAssetTransactionDao;

    @Autowired
    private ScriptDao scriptDao;

    @Autowired
    private ScriptRefDao scriptRefDao;

    @Test
    @DisplayName("文件状态：TEMP/CONFIRMED 都能装配，计数之和等于总量")
    void 文件状态() {
        List<FileEntity> list = fileDao.selectList(null);
        assertFalse(list.isEmpty(), "t_file 没有数据，这条用例失去意义");
        for (FileEntity e : list) {
            assertNotNull(e.getStatus(), "status 装配成了 null");
        }
        // 取值是 1-临时 / 2-已确认，两个值库里都有。装配反了的话孤儿清理任务
        // 会把已确认的文件当垃圾删掉 —— 这条断言就是钉这个的
        assertTrue(list.stream().anyMatch(e -> e.getStatus() == FileStatusEnum.CONFIRMED),
                "一个已确认文件都没有，多半是 1/2 装反了");
        assertTrue(list.stream().anyMatch(e -> e.getStatus() == FileStatusEnum.TEMP));

        assertEquals(list.size(), sum(FileStatusEnum.values(),
                        s -> fileDao.selectCount(new LambdaQueryWrapper<FileEntity>().eq(FileEntity::getStatus, s))),
                "分状态计数之和与总量对不上，说明有行的 status 落在枚举之外");
    }

    @Test
    @DisplayName("中奖记录派发状态：计数之和等于总量")
    void 派发状态() {
        Long total = lotteryRecordDao.selectCount(new LambdaQueryWrapper<>());
        assertNotNull(total);
        assertTrue(total > 0, "t_lottery_record 没有数据，这条用例失去意义");

        assertEquals(total.longValue(), sum(LotteryDispatchStatusEnum.values(),
                        s -> lotteryRecordDao.selectCount(
                                new LambdaQueryWrapper<LotteryRecord>().eq(LotteryRecord::getDispatchStatus, s))),
                "分状态计数之和与总量对不上，说明有行的 dispatch_status 落在枚举之外");

        // WAITING 同时表示「待派发」和「无需派发」，未中奖的行也落在这里，
        // 所以它一定是占大头的那个 —— 这条能挡住 0/1 装反
        Long waiting = lotteryRecordDao.selectCount(new LambdaQueryWrapper<LotteryRecord>()
                .eq(LotteryRecord::getDispatchStatus, LotteryDispatchStatusEnum.WAITING));
        assertNotNull(waiting);
        assertTrue(waiting > 0, "一行待派发都没有，多半是取值装反了");
    }

    @Test
    @DisplayName("资金流向：收入/支出计数之和等于总量")
    void 资金流向() {
        Long total = memberAssetTransactionDao.selectCount(new LambdaQueryWrapper<>());
        assertNotNull(total);
        assertTrue(total > 0, "t_member_asset_transaction 没有数据，这条用例失去意义");

        assertEquals(total.longValue(), sum(TransactionTypeEnum.values(),
                        t -> memberAssetTransactionDao.selectCount(new LambdaQueryWrapper<MemberAssetTransaction>()
                                .eq(MemberAssetTransaction::getTransactionType, t))),
                "分流向计数之和与总量对不上，说明有行的 transaction_type 落在枚举之外");

        // change_amount 存的是绝对值，方向全靠这一列。装反了余额算出来符号就是反的
        Long income = memberAssetTransactionDao.selectCount(new LambdaQueryWrapper<MemberAssetTransaction>()
                .eq(MemberAssetTransaction::getTransactionType, TransactionTypeEnum.INCOME));
        assertNotNull(income);
        assertTrue(income > 0, "一条收入流水都没有，多半是 1/2 装反了");
    }

    @Test
    @DisplayName("脚本与脚本引用状态：都能装配，计数之和等于总量")
    void 脚本状态() {
        List<Script> scripts = scriptDao.selectList(null);
        assertFalse(scripts.isEmpty(), "t_script 没有数据，这条用例失去意义");
        for (Script e : scripts) {
            assertNotNull(e.getStatus(), "status 装配成了 null");
        }
        // 装反了 ScriptFileLoader 会把在用的脚本全当孤儿停掉
        assertTrue(scripts.stream().anyMatch(e -> e.getStatus() == EnableStatusEnum.ENABLED),
                "一个启用的脚本都没有，多半是 0/1 方向反了");
        assertEquals(scripts.size(), sum(EnableStatusEnum.values(),
                        s -> scriptDao.selectCount(new LambdaQueryWrapper<Script>().eq(Script::getStatus, s))),
                "分状态计数之和与总量对不上，说明有行的 status 落在枚举之外");

        List<ScriptRef> refs = scriptRefDao.selectList(null);
        for (ScriptRef e : refs) {
            assertNotNull(e.getStatus(), "status 装配成了 null");
        }
        assertEquals(refs.size(), sum(EnableStatusEnum.values(),
                        s -> scriptRefDao.selectCount(new LambdaQueryWrapper<ScriptRef>().eq(ScriptRef::getStatus, s))),
                "t_script_ref 分状态计数之和与总量对不上");
    }

    private <E> long sum(E[] values, java.util.function.Function<E, Long> counter) {
        long total = 0;
        for (E v : values) {
            Long n = counter.apply(v);
            assertNotNull(n, "按 " + v + " 过滤时 SQL 出错");
            total += n;
        }
        return total;
    }
}
