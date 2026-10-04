package kitejs.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import net.minecraft.client.Minecraft;

public final class HttpQueries {
	private static final int CONNECT_TIMEOUT = 30_000;
	private static final int READ_TIMEOUT = 10_000;

	private HttpQueries() {
	}

	public static Optional<QueryResult> query(String url) {
		HttpURLConnection connection = null;

		try {
			connection = (HttpURLConnection) URI.create(url).toURL().openConnection(Minecraft.getInstance().getProxy());
			connection.setConnectTimeout(CONNECT_TIMEOUT);
			connection.setReadTimeout(READ_TIMEOUT);
			connection.setUseCaches(false);
			connection.setDoInput(true);

			int status = connection.getResponseCode();

			if (status / 100 == 2) {
				return Optional.of(new QueryResult(status, readBody(connection)));
			}

			if (status == HttpURLConnection.HTTP_NOT_FOUND || status == HttpURLConnection.HTTP_NO_CONTENT) {
				return Optional.of(new QueryResult(status, ""));
			}

			return Optional.empty();
		} catch (Exception ignored) {
			return Optional.empty();
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	private static String readBody(HttpURLConnection connection) throws IOException {
		try (InputStream stream = connection.getInputStream()) {
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	public record QueryResult(int status, String body) {
	}
}
