package solvela.base.storage;

/**
 * 存储层失败原因。<b>类型即语义。</b>
 *
 * <p><b>存储层不认识 HTTP</b> —— 让基础设施层返回一个响应结构，
 * 就意味着这一层永远只能被 Controller 调，定时任务、MQ 消费者、单测都用不了。
 * 旧的 {@code IFileStorageService} 就是这么长的。
 *
 * <p>做成 sealed 的目的和 {@code SonicErrorPolicy} / {@code RowConstructor} 一样：
 * 让上层的 {@code switch} 被编译器检查完整性，新增一种失败时所有遗漏点会被指出来。
 *
 * @Date 2026-08-09
 */
public sealed interface StorageFailure {

    /**
     * 给人看的说明。会成为 {@link StorageException} 的 message，所以要写成运维能照着排查的话。
     */
    String describe();

    record NotFound(StorageKey key) implements StorageFailure {
        @Override
        public String describe() {
            return "对象不存在：" + key;
        }
    }

    record QuotaExceeded(long limitBytes, long actualBytes) implements StorageFailure {
        @Override
        public String describe() {
            return "对象超过大小上限：" + actualBytes + " > " + limitBytes + " 字节";
        }
    }

    /**
     * 连不上存储（网络、端点配置错、凭证失效）。这一类通常是<b>环境问题不是数据问题</b>，
     * 值得和其他失败分开，因为处置方式完全不同：重试有意义，而 NotFound 重试没意义。
     */
    record Unreachable(String target) implements StorageFailure {
        @Override
        public String describe() {
            return "存储不可达：" + target;
        }
    }

    record Corrupted(String reason) implements StorageFailure {
        @Override
        public String describe() {
            return "对象读写异常：" + reason;
        }
    }
}
