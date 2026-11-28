package solvela.admin.file;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import solvela.base.module.file.constant.FileStatusEnum;
import solvela.base.module.file.dao.FileDao;
import solvela.base.module.file.domain.entity.FileEntity;
import solvela.base.module.file.service.FileAssetService;
import solvela.exception.BusinessException;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务确认引用与孤儿清理同时碰一个文件，在真库上用两个事务复现。
 *
 * <p>以前清理是「读到临时、无引用 → 无条件标删除 → 删字节」，业务的 confirm 恰好夹在中间提交的话，
 * 刚配好的图字节就没了。现在 confirm 先锁行，清理的判据压在 UPDATE 的 WHERE 里：清理只能等 confirm 提交，
 * 提交之后再判时已是已确认，影响 0 行。
 *
 * <p>直接调清理的那条 UPDATE 而不是 {@code purgeOrphans}：后者会扫全库的孤儿，在开发库上跑这条用例会真删别人的临时文件。
 * 不加 {@code @Transactional}：两个事务要真的并发，数据结束时手动删掉。
 */
@SpringBootTest
class FileConfirmPurgeRaceTest {

    private static final String BIZ_TYPE = "ZZ_RACE_TEST";

    @Autowired
    private FileAssetService fileAssetService;
    @Autowired
    private FileDao fileDao;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long fileId;

    @BeforeEach
    void setUp() {
        Long categoryId = jdbcTemplate.queryForObject("SELECT MIN(category_id) FROM t_file_category", Long.class);
        FileEntity file = new FileEntity();
        file.setCategoryId(categoryId);
        file.setStorageKey("zz-race/" + System.nanoTime() + ".png");
        file.setStorageKind("LOCAL");
        file.setOriginalName("race.png");
        file.setExtension("png");
        file.setContentType("image/png");
        file.setFileSize(1L);
        file.setDeletedFlag(false);
        file.startTemp();
        fileDao.insert(file);
        fileId = file.getFileId();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM t_file_relation WHERE file_id = ?", fileId);
        jdbcTemplate.update("DELETE FROM t_file WHERE file_id = ?", fileId);
    }

    @Test
    @DisplayName("🔴 confirm 还没提交时清理只能等；confirm 提交后清理影响 0 行，文件是已确认、没被删")
    void 清理等确认提交后放弃() throws Exception {
        CountDownLatch confirmed = new CountDownLatch(1);
        CountDownLatch releaseCommit = new CountDownLatch(1);
        CompletableFuture<Void> business = CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(s -> {
            fileAssetService.confirm(List.of(fileId), BIZ_TYPE, 1L);
            confirmed.countDown();
            try {
                assertTrue(releaseCommit.await(30, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }));
        assertTrue(confirmed.await(30, TimeUnit.SECONDS), "前提不成立：业务事务没走到 confirm 之后");

        CompletableFuture<Integer> purge = CompletableFuture.supplyAsync(() -> fileDao.softDeleteOrphan(fileId));
        Thread.sleep(1000);
        // 证明清理确实被行锁挡住了，而不是碰巧在 confirm 之后才开始
        assertFalse(purge.isDone(), "清理没有等 confirm 的行锁就返回了");

        releaseCommit.countDown();
        business.get(30, TimeUnit.SECONDS);
        assertEquals(0, purge.get(30, TimeUnit.SECONDS), "confirm 提交后清理仍然标了删除");

        FileEntity after = fileDao.selectById(fileId);
        assertEquals(FileStatusEnum.CONFIRMED, after.getStatus());
        assertFalse(after.getDeletedFlag(), "已确认、在用的文件被清理标了删除");
    }

    @Test
    @DisplayName("清理先完成：之后的 confirm 锁不到这个文件，整次拒绝，不会把已删的文件确认成在用")
    void 清理在先确认被拒() {
        assertEquals(1, fileDao.softDeleteOrphan(fileId));

        assertThrows(BusinessException.class, () -> fileAssetService.confirm(List.of(fileId), BIZ_TYPE, 1L));
        assertEquals(FileStatusEnum.TEMP, fileDao.selectById(fileId).getStatus());
    }
}
