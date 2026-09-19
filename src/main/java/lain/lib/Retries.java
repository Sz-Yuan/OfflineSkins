package lain.lib;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

// 重试工具：仅保留下载链路用到的 Run + rethrow
public final class Retries {

    private Retries() {
        throw new Error("NoInstance");
    }

    // 原样抛出（用于 Consumer/回调里吞掉返回值）
    public static void rethrow(Throwable throwable) {
        rethrow0(throwable);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void rethrow0(Throwable throwable) throws T {
        throw (T) throwable;
    }

    public static ThrowingRun retrying(ThrowingRun action, Predicate<Throwable> shouldRetry, Consumer<Integer> beforeRetry, int maxRetries) {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicInteger retries = new AtomicInteger();
        return () -> {
            while (true) {
                try {
                    if (thrown.get() != null && beforeRetry != null)
                        beforeRetry.accept(retries.get());
                    action.run();
                    return;
                } catch (Throwable throwable) {
                    if (!thrown.compareAndSet(null, throwable))
                        thrown.get().addSuppressed(throwable);
                    if ((shouldRetry != null && !shouldRetry.test(throwable)) || retries.getAndIncrement() == maxRetries)
                        break;
                }
            }
            throw thrown.get();
        };
    }

    @FunctionalInterface
    public interface ThrowingRun {

        void run() throws Throwable;

        // 转为 Runnable；异常交给 handler
        default Runnable toRunnable(Consumer<Throwable> handler) {
            return () -> {
                try {
                    run();
                } catch (Throwable throwable) {
                    if (handler != null)
                        handler.accept(throwable);
                }
            };
        }

    }

}
