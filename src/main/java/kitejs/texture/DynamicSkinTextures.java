package kitejs.texture;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import kitejs.OfflineSkins;
import kitejs.data.SkinData;

public final class DynamicSkinTextures {
	private static final Map<ByteBuffer, Identifier> TEXTURES = new WeakHashMap<>();

	private DynamicSkinTextures() {
	}

	public static Identifier resolve(SkinData data) {
		if (data == null || !data.isDataReady()) {
			return null;
		}

		ByteBuffer buffer = data.getData();

		if (buffer == null) {
			return null;
		}

		synchronized (TEXTURES) {
			Identifier existing = TEXTURES.get(buffer);

			if (existing != null) {
				return existing;
			}
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

		synchronized (TEXTURES) {
			TEXTURES.put(buffer, identifier);
		}

		data.addListener(removed -> {
			if (removed.getData() != buffer) {
				return;
			}

			minecraft.execute(() -> release(identifier, buffer));
		});

		return identifier;
	}

	private static void release(Identifier identifier, ByteBuffer buffer) {
		Minecraft.getInstance().getTextureManager().release(identifier);

		synchronized (TEXTURES) {
			if (TEXTURES.get(buffer) == identifier) {
				TEXTURES.remove(buffer);
			}
		}
	}
}
