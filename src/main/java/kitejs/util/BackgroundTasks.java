package kitejs.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BackgroundTasks {
	private static final int MAX_CONCURRENT = 4;
	private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
		MAX_CONCURRENT,
		Thread.ofPlatform().daemon().name("OfflineSkins-Worker-", 0L).factory()
	);

	private BackgroundTasks() {
	}

	public static void execute(Runnable task) {
		EXECUTOR.execute(task);
	}
}
