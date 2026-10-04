package kitejs.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import kitejs.OfflineSkins;

public final class BackgroundTasks {
	public static final int DEFAULT_THREADS = 8;
	private static final int MIN_THREADS = 1;
	private static final int MAX_THREADS = 32;

	private static ExecutorService executor = create(DEFAULT_THREADS);
	private static int threads = DEFAULT_THREADS;

	private BackgroundTasks() {
	}

	public static void configure(int count) {
		int clamped = Math.clamp(count, MIN_THREADS, MAX_THREADS);

		if (clamped == threads) {
			return;
		}

		ExecutorService previous = executor;

		threads = clamped;
		executor = create(clamped);
		previous.shutdown();
	}

	public static int threads() {
		return threads;
	}

	public static void execute(Runnable task) {
		try {
			executor.execute(task);
		} catch (RejectedExecutionException e) {
			OfflineSkins.LOGGER.warn("Background task rejected while the worker pool was being reconfigured", e);
		}
	}

	private static ExecutorService create(int count) {
		return Executors.newFixedThreadPool(count, Thread.ofPlatform().daemon().name("OfflineSkins-Worker-", 0L).factory());
	}
}
