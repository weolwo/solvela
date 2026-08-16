package sa.task.runtime;

/**
 * 并发冲突信号：本次推进没打成，需要<b>换一个事务</b>整体重来。
 *
 * <p>有两个来源，共性是「重试必须发生在新事务里」：
 * <ol>
 *   <li><b>STREAK 的乐观锁冲突</b> —— 版本号已被别人改掉；</li>
 *   <li><b>任务记录被并发创建</b> —— INSERT 撞 {@code uk_t_tsk_rec_mbr_cfg_prd}。
 *       此时<b>不能就地重查</b>：MySQL 默认 REPEATABLE READ，本事务的快照在第一次读时就固定了，
 *       赢家后提交的那一行不在快照里，重查依然是 null。必须换事务拿新快照。</li>
 * </ol>
 *
 * <p><b>为什么必须抛异常而不是返回一个「请重试」的结果对象</b>：
 * 事件流水是<b>先于</b>进度推进落库的（幂等键要先占住）。若在同一个事务里正常返回，
 * 事务会提交，那行流水就留下了 —— 下一次重试立刻撞唯一索引被判成「已处理」，
 * 进度<b>永远推不上去</b>，而且外部看起来一切正常（事件收到了、流水也有）。
 *
 * <p>抛异常让 {@code @Transactional(rollbackFor = Exception.class)} 把流水一并回滚，
 * 重试才能真正重来。这也是 {@code TaskRecordAdvanceService} 必须是<b>独立 Bean</b> 的原因之一：
 * 每次重试都是一个全新的事务，上一次的失败不会把后续尝试拖进 rollback-only 状态
 * （交接文档里 {@code UnexpectedRollbackException} 那个坑就是这么来的）。
 *
 * @author alaric
 * @date 2026-08-01
 */
public class TaskConcurrentModifyException extends RuntimeException {

    public TaskConcurrentModifyException(String message) {
        super(message);
    }
}
