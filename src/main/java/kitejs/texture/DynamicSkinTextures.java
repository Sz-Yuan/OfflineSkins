package kitejs.texture;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import kitejs.OfflineSkins;
import kitejs.data.SkinData;

public final class DynamicSkinTextures {
	private static final Map<String, Identifier> TEXTURES = new ConcurrentHashMap<>();

	private DynamicSkinTextures() {
	}

	public static int size() {
		return TEXTURES.size();
	}

	public static Identifier resolve(SkinData data) {
		if (data == null || !data.isDataReady()) {
			return null;
		}

		String key = data.getContentHash();

		if (key == null) {
			return null;
		}

		Identifier existing = TEXTURES.get(key);

		if (existing != null) {
			return existing;
		}

		ByteBuffer buffer = data.getData();

		if (buffer == null) {
			return null;
		}

		NativeImage image;

		try {
			image = NativeImage.read(buffer);
		} catch (Exception e) {
			return null;
		}

		NativeImage fixed = SkinData.TYPE_CAPE.equals(data.getType()) ? image : SkinImageFix.apply(image);

		if (fixed == null) {
			image.close();

			return null;
		}

		Minecraft minecraft = Minecraft.getInstance();
		Identifier identifier = Identifier.fromNamespaceAndPath(OfflineSkins.MOD_ID, "textures/generated/" + UUID.randomUUID());

		try {
			minecraft.getTextureManager().register(identifier, new DynamicTexture(identifier::toString, fixed));
		} catch (Exception e) {
			fixed.close();

			return null;
		}

		TEXTURES.put(key, identifier);

		data.addListener(removed -> {
			if (removed.getData() != buffer) {
				return;
			}

			minecraft.execute(() -> release(identifier, key));
		});

		return identifier;
	}

	private static void release(Identifier identifier, String key) {
		Minecraft.getInstance().getTextureManager().release(identifier);
		TEXTURES.remove(key, identifier);
	}
}
