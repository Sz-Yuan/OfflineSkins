package kitejs.util;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class WorkerPool {
	private WorkerPool() {
	}

	public static void execute(Runnable task) {
		throw new UnsupportedOperationException("第 11 步实现");
	}

	public static <T> CompletableFuture<T> submit(Supplier<T> task) {
		throw new UnsupportedOperationException("第 11 步实现");
	}
}
