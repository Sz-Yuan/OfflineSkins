package kitejs.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
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
			Path file = null;

			try {
				file = Files.createTempFile("offlineskins", ".tmp");

				HttpURLConnection connection = open(target);

				try {
					if (connection.getResponseCode() / 100 == 4) {
						return Optional.empty();
					}

					try (InputStream input = connection.getInputStream(); OutputStream output = Files.newOutputStream(file)) {
						input.transferTo(output);
					}
				} finally {
					connection.disconnect();
				}

				return Optional.of(Files.readAllBytes(file));
			} catch (IOException ignored) {
				pause();
			} catch (Exception ignored) {
				return Optional.empty();
			} finally {
				deleteQuietly(file);
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

	private static void deleteQuietly(Path file) {
		if (file == null) {
			return;
		}

		try {
			Files.deleteIfExists(file);
		} catch (IOException ignored) {
			file.toFile().deleteOnExit();
		}
	}
}
