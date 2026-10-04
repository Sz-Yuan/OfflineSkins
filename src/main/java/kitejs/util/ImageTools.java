package kitejs.util;

import com.mojang.blaze3d.platform.NativeImage;

import kitejs.data.SkinData;

public final class ImageTools {
	private ImageTools() {
	}

	public static boolean isValidPng(byte[] bytes) {
		try (NativeImage image = NativeImage.read(bytes)) {
			return image.getWidth() > 0;
		} catch (Exception ignored) {
			return false;
		}
	}

	public static String detectSkinType(byte[] bytes) {
		try (NativeImage image = NativeImage.read(bytes)) {
			int width = image.getWidth();
			int height = image.getHeight();

			if (width == height * 2) {
				return SkinData.TYPE_DEFAULT;
			}

			if (width != height) {
				return SkinData.TYPE_UNKNOWN;
			}

			int scale = Math.max(width / 64, 1);

			return image.getLuminanceOrAlpha(55 * scale, 20 * scale) == 0 ? SkinData.TYPE_SLIM : SkinData.TYPE_DEFAULT;
		} catch (Exception ignored) {
			return SkinData.TYPE_UNKNOWN;
		}
	}
}
