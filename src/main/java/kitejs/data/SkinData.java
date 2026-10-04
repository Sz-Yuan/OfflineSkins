package kitejs.data;

import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.function.Function;

public class SkinData {
	public static final String TYPE_DEFAULT = "default";
	public static final String TYPE_SLIM = "slim";
	public static final String TYPE_CAPE = "cape";
	public static final String TYPE_UNKNOWN = "unknown";

	public synchronized void put(byte[] bytes, String type) {
		throw new UnsupportedOperationException("第 2 步实现");
	}

	public synchronized boolean isDataReady() {
		throw new UnsupportedOperationException("第 2 步实现");
	}

	public synchronized ByteBuffer getData() {
		throw new UnsupportedOperationException("第 2 步实现");
	}

	public synchronized String getType() {
		throw new UnsupportedOperationException("第 2 步实现");
	}

	public void addFilter(Function<ByteBuffer, ByteBuffer> filter) {
		throw new UnsupportedOperationException("第 2 步实现");
	}

	public void addListener(Consumer<SkinData> listener) {
		throw new UnsupportedOperationException("第 2 步实现");
	}

	public void onRemoval() {
		throw new UnsupportedOperationException("第 2 步实现");
	}
}
