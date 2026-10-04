package kitejs.util;

import java.nio.ByteBuffer;
import java.util.function.Function;

public final class LegacySkinConverter implements Function<ByteBuffer, ByteBuffer> {
	public static final LegacySkinConverter INSTANCE = new LegacySkinConverter();

	private LegacySkinConverter() {
	}

	@Override
	public ByteBuffer apply(ByteBuffer input) {
		throw new UnsupportedOperationException("第 10 步实现");
	}
}
