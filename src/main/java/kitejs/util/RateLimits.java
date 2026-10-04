package kitejs.util;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import kitejs.OfflineSkins;

public final class RateLimits {
	public static final int TOO_MANY_REQUESTS = 429;
	private static final long MIN_BACKOFF = 60_000L;
	private static final long MAX_BACKOFF = 15L * 60_000L;
	private static final Map<String, Long> BLOCKED_UNTIL = new ConcurrentHashMap<>();
	private static final Map<String, Integer> SKIPPED = new ConcurrentHashMap<>();

	private RateLimits() {
	}

	public static String host(URI uri) {
		String host = uri.getHost();

		return host == null ? uri.toString() : host;
	}

	public static long retryAfterMillis(HttpURLConnection connection) {
		String header = connection.getHeaderField("Retry-After");

		if (header != null) {
			try {
				long seconds = Long.parseLong(header.trim());

				if (seconds > 0L) {
					return seconds * 1000L;
				}
			} catch (NumberFormatException ignored) {
			}
		}

		return 0L;
	}

	public static boolean isBlocked(String host) {
		Long until = BLOCKED_UNTIL.get(host);

		return until != null && System.currentTimeMillis() < until;
	}

	public static void noteSkipped(String host) {
		SKIPPED.merge(host, 1, Integer::sum);
	}

	public static void block(String host, long millis) {
		long backoff = Math.clamp(millis, MIN_BACKOFF, MAX_BACKOFF);
		BLOCKED_UNTIL.put(host, System.currentTimeMillis() + backoff);

		Integer skipped = SKIPPED.remove(host);
		int count = skipped == null ? 0 : skipped;

		if (count > 0) {
			OfflineSkins.LOGGER.warn("{} is rate limited again, pausing requests for {}s ({} requests skipped in the previous window)", host, backoff / 1000L, count);
		} else {
			OfflineSkins.LOGGER.warn("{} returned HTTP 429, pausing requests for {}s", host, backoff / 1000L);
		}
	}
}
