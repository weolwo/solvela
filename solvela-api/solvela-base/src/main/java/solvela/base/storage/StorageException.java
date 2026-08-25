package solvela.base.storage;

/**
 * 存储层异常，携带一个 {@link StorageFailure} 说明失败类型。
 *
 * <p>刻意<b>不继承 {@code BusinessException}</b>：存储层是基础设施，不该知道业务异常体系的存在。
 * 翻译成 {@code ResponseDTO} 这件事只在 Controller 层发生一次。
 *
 * @Date 2026-08-09
 */
public class StorageException extends RuntimeException {

    private final transient StorageFailure failure;

    public StorageException(StorageFailure failure) {
        super(failure.describe());
        this.failure = failure;
    }

    public StorageException(StorageFailure failure, Throwable cause) {
        super(failure.describe(), cause);
        this.failure = failure;
    }

    public StorageFailure failure() {
        return failure;
    }

    // ------------------------------------------------------------------ 快捷构造

    public static StorageException notFound(StorageKey key) {
        return new StorageException(new StorageFailure.NotFound(key));
    }

    public static StorageException notFound(StorageKey key, Throwable cause) {
        return new StorageException(new StorageFailure.NotFound(key), cause);
    }

    public static StorageException unreachable(String target, Throwable cause) {
        return new StorageException(new StorageFailure.Unreachable(target), cause);
    }

    public static StorageException corrupted(String reason, Throwable cause) {
        return new StorageException(new StorageFailure.Corrupted(reason), cause);
    }
}
