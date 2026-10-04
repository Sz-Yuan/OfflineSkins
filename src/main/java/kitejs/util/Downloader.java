package kitejs.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Optional;

import net.minecraft.client.Minecraft;

public final class Downloader {
	private static final int CONNECT_TIMEOUT = 10_000;
	private static final int READ_TIMEOUT = 30_000;
	private static final int ATTEMPTS = 3;
	private static final long RETRY_DELAY = 1_000L;

	private Downloader() {
	}

	public static Optional<byte[]> download(String url) {
		String target = normalize(url);

		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			HttpURLConnection connection = null;

			try {
				connection = open(target);

				if (connection.getResponseCode() / 100 == 4) {
					return Optional.empty();
				}

				try (InputStream input = connection.getInputStream()) {
					ByteArrayOutputStream output = new ByteArrayOutputStream();
					input.transferTo(output);

					return Optional.of(output.toByteArray());
				}
			} catch (IOException ignored) {
				pause();
			} catch (Exception ignored) {
				return Optional.empty();
			} finally {
				if (connection != null) {
					connection.disconnect();
				}
			}
		}

		return Optional.empty();
	}

	private static String normalize(String url) {
		try {
			return URI.create(url).toASCIIString();
		} catch (Exception ignored) {
			return url;
		}
	}

	private static HttpURLConnection open(String url) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection(Minecraft.getInstance().getProxy());
		connection.setConnectTimeout(CONNECT_TIMEOUT);
		connection.setReadTimeout(READ_TIMEOUT);
		connection.setUseCaches(true);
		connection.setDoInput(true);

		return connection;
	}

	private static void pause() {
		try {
			Thread.sleep(RETRY_DELAY);
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		}
	}
}
