package kitejs.data;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

import kitejs.OfflineSkins;

public class SkinData {
	public static final String TYPE_DEFAULT = "default";
	public static final String TYPE_SLIM = "slim";
	public static final String TYPE_CAPE = "cape";
	public static final String TYPE_UNKNOWN = "unknown";

	private final List<Function<ByteBuffer, ByteBuffer>> filters = new CopyOnWriteArrayList<>();
	private final List<Consumer<SkinData>> listeners = new CopyOnWriteArrayList<>();

	private ByteBuffer data;
	private String type;
	private String contentHash;
	private volatile boolean unavailable;

	public void put(byte[] bytes, String type) {
		if (bytes == null) {
			return;
		}

		ByteBuffer buffer = toDirectBuffer(bytes);

		for (Function<ByteBuffer, ByteBuffer> filter : filters) {
			buffer = filter.apply(buffer);

			if (buffer == null) {
				return;
			}
		}

		synchronized (this) {
			this.data = buffer;
			this.type = type;
			this.contentHash = contentHash(buffer);
		}
	}

	public synchronized boolean isDataReady() {
		return data != null;
	}

	public synchronized void markUnavailable() {
		unavailable = true;
	}

	public synchronized boolean isUnavailable() {
		return data == null && unavailable;
	}

	public synchronized String getContentHash() {
		return contentHash;
	}

	private static String contentHash(ByteBuffer buffer) {
		ByteBuffer view = buffer.duplicate();

		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(view);

			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			return Integer.toHexString(System.identityHashCode(buffer));
		}
	}

	public synchronized ByteBuffer getData() {
		return data;
	}

	public synchronized String getType() {
		return type;
	}

	public void addFilter(Function<ByteBuffer, ByteBuffer> filter) {
		if (filter == null || containsIdentity(filters, filter)) {
			return;
		}

		filters.add(filter);
	}

	public void addListener(Consumer<SkinData> listener) {
		if (listener == null || containsIdentity(listeners, listener)) {
			return;
		}

		listeners.add(listener);
	}

	public void onRemoval() {
		notifyListeners();

		synchronized (this) {
			data = null;
			type = null;
		}
	}

	protected final void notifyListeners() {
		for (Consumer<SkinData> listener : listeners) {
			try {
				listener.accept(this);
			} catch (RuntimeException e) {
				OfflineSkins.LOGGER.warn("Skin removal listener threw an exception", e);
			}
		}
	}

	protected final List<Function<ByteBuffer, ByteBuffer>> filters() {
		return filters;
	}

	protected final List<Consumer<SkinData>> listeners() {
		return listeners;
	}

	private static ByteBuffer toDirectBuffer(byte[] bytes) {
		ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);
		buffer.put(bytes);
		buffer.flip();
		buffer.order(ByteOrder.nativeOrder());
		return buffer;
	}

	private static <T> boolean containsIdentity(List<T> list, T value) {
		for (T element : list) {
			if (element == value) {
				return true;
			}
		}

		return false;
	}
}
