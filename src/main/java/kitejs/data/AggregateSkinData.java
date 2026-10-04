package kitejs.data;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;

public class AggregateSkinData extends SkinData {
	public void set(Collection<SkinData> members) {
		throw new UnsupportedOperationException("第 2 步实现");
	}

	@Override
	public boolean isDataReady() {
		throw new UnsupportedOperationException("第 2 步实现");
	}

	@Override
	public ByteBuffer getData() {
		throw new UnsupportedOperationException("第 2 步实现");
	}

	@Override
	public String getType() {
		throw new UnsupportedOperationException("第 2 步实现");
	}

	@Override
	public void addFilter(Function<ByteBuffer, ByteBuffer> filter) {
		throw new UnsupportedOperationException("第 2 步实现");
	}

	@Override
	public void addListener(Consumer<SkinData> listener) {
		throw new UnsupportedOperationException("第 2 步实现");
	}

	@Override
	public void onRemoval() {
		throw new UnsupportedOperationException("第 2 步实现");
	}
}
