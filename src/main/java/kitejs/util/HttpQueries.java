package kitejs.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import kitejs.OfflineSkins;

public final class HttpQueries {
	private static final int CONNECT_TIMEOUT = 30_000;
	private static final int READ_TIMEOUT = 10_000;
	private static final String USER_AGENT_NAME = "OfflineSkins";
	public static final String USER_AGENT = FabricLoader.getInstance()
		.getModContainer(OfflineSkins.MOD_ID)
		.map(container -> USER_AGENT_NAME + "/" + container.getMetadata().getVersion().getFriendlyString())
		.orElse(USER_AGENT_NAME);

	private HttpQueries() {
	}

	public static Optional<QueryResult> query(String url) {
		HttpURLConnection connection = null;

		try {
			URI uri = URI.create(url);
			String host = RateLimits.host(uri);

			if (RateLimits.isBlocked(host)) {
				RateLimits.noteSkipped(host);

				return Optional.empty();
			}

			connection = (HttpURLConnection) uri.toURL().openConnection(Minecraft.getInstance().getProxy());
			connection.setConnectTimeout(CONNECT_TIMEOUT);
			connection.setReadTimeout(READ_TIMEOUT);
			connection.setUseCaches(false);
			connection.setDoInput(true);
			connection.setRequestProperty("User-Agent", USER_AGENT);

			int status = connection.getResponseCode();

			if (status == RateLimits.TOO_MANY_REQUESTS) {
				RateLimits.block(host, RateLimits.retryAfterMillis(connection));

				return Optional.empty();
			}

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
