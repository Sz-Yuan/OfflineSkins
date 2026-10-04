package kitejs.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.util.ARGB;

import kitejs.OfflineSkins;

public final class LegacySkinConverter implements Function<ByteBuffer, ByteBuffer> {
	public static final LegacySkinConverter INSTANCE = new LegacySkinConverter();

	private static final int SKIN_WIDTH = 64;
	private static final int SKIN_HEIGHT = 64;
	private static final int LEGACY_SKIN_HEIGHT = 32;
	private static final Region HEAD = new Region(0, 0, 32, 16);
	private static final Region BODY = new Region(0, 16, 64, 32);
	private static final Region LIMBS = new Region(16, 48, 48, 64);

	private LegacySkinConverter() {
	}

	@Override
	public ByteBuffer apply(ByteBuffer input) {
		try (NativeImage source = NativeImage.read(input.duplicate())) {
			int width = source.getWidth();
			int height = source.getHeight();

			if (width != SKIN_WIDTH || (height != LEGACY_SKIN_HEIGHT && height != SKIN_HEIGHT)) {
				OfflineSkins.LOGGER.warn("皮肤尺寸 {}x{} 不受支持，已忽略（原版只接受 64x32 与 64x64）", width, height);
				return null;
			}

			return convert(source);
		} catch (Exception e) {
			return input;
		}
	}

	private static ByteBuffer convert(NativeImage source) throws IOException {
		boolean legacy = source.getHeight() == LEGACY_SKIN_HEIGHT;

		try (NativeImage canvas = new NativeImage(SKIN_WIDTH, SKIN_HEIGHT, true)) {
			canvas.copyFrom(source);

			if (legacy) {
				canvas.fillRect(0, 32, 64, 32, 0);
				canvas.copyRect(4, 16, 16, 32, 4, 4, true, false);
				canvas.copyRect(8, 16, 16, 32, 4, 4, true, false);
				canvas.copyRect(0, 20, 24, 32, 4, 12, true, false);
				canvas.copyRect(4, 20, 16, 32, 4, 12, true, false);
				canvas.copyRect(8, 20, 8, 32, 4, 12, true, false);
				canvas.copyRect(12, 20, 16, 32, 4, 12, true, false);
				canvas.copyRect(44, 16, -8, 32, 4, 4, true, false);
				canvas.copyRect(48, 16, -8, 32, 4, 4, true, false);
				canvas.copyRect(40, 20, 0, 32, 4, 12, true, false);
				canvas.copyRect(44, 20, -8, 32, 4, 12, true, false);
				canvas.copyRect(48, 20, -16, 32, 4, 12, true, false);
				canvas.copyRect(52, 20, -8, 32, 4, 12, true, false);
			}

			forceOpaque(canvas, HEAD);

			if (legacy) {
				notchTransparencyHack(canvas);
			}

			forceOpaque(canvas, BODY);
			forceOpaque(canvas, LIMBS);

			return encode(canvas);
		}
	}

	private static void forceOpaque(NativeImage image, Region region) {
		for (int x = region.x0(); x < region.x1(); x++) {
			for (int y = region.y0(); y < region.y1(); y++) {
				image.setPixel(x, y, ARGB.opaque(image.getPixel(x, y)));
			}
		}
	}

	private static void notchTransparencyHack(NativeImage image) {
		for (int x = 32; x < 64; x++) {
			for (int y = 0; y < 32; y++) {
				if (ARGB.alpha(image.getPixel(x, y)) < 128) {
					return;
				}
			}
		}

		for (int x = 32; x < 64; x++) {
			for (int y = 0; y < 32; y++) {
				image.setPixel(x, y, ARGB.transparent(image.getPixel(x, y)));
			}
		}
	}

	private static ByteBuffer encode(NativeImage image) throws IOException {
		Path directory = Files.createTempDirectory("offlineskins");
		Path file = directory.resolve("skin.png");

		try {
			image.writeToFile(file);

			byte[] bytes = Files.readAllBytes(file);
			ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);
			buffer.put(bytes);
			buffer.flip();
			buffer.order(ByteOrder.nativeOrder());

			return buffer;
		} finally {
			Files.deleteIfExists(file);
			Files.deleteIfExists(directory);
		}
	}

	private record Region(int x0, int y0, int x1, int y1) {
	}
}
