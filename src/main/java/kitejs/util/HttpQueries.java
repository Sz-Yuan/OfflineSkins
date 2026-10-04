package kitejs.util;

import java.util.Optional;

public final class HttpQueries {
	private HttpQueries() {
	}

	public static Optional<QueryResult> query(String url) {
		throw new UnsupportedOperationException("第 11 步实现");
	}

	public record QueryResult(int status, String body) {
	}
}
