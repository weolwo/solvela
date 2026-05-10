package net.lab1024.sa.scriptengine.spi;

public class EngineContextHolder {

    private static final ThreadLocal<EngineContext> CONTEXT_HOLDER = new ThreadLocal<>();

    public static void set(EngineContext context) {
        CONTEXT_HOLDER.set(context);
    }

    public static EngineContext get() {
        return CONTEXT_HOLDER.get();
    }

    public static void clear() {
        CONTEXT_HOLDER.remove();
    }
}
