package kitejs.texture;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import kitejs.OfflineSkins;
import kitejs.data.SkinData;

public final class DynamicSkinTextures {
	private static final Object LOCK = new Object();
	private static final Map<String, Entry> TEXTURES = new ConcurrentHashMap<>();
	private static final Map<SkinData, String> RESOLVED = new WeakHashMap<>();

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

		synchronized (LOCK) {
			if (key.equals(RESOLVED.get(data))) {
				Entry known = TEXTURES.get(key);

				if (known != null) {
					return known.identifier;
				}
			}

			Entry entry = TEXTURES.get(key);

			if (entry != null) {
				entry.users++;
				RESOLVED.put(data, key);
				attach(data, key);

				return entry.identifier;
			}

			Identifier identifier = register(data);

			if (identifier == null) {
				return null;
			}

			TEXTURES.put(key, new Entry(identifier));
			RESOLVED.put(data, key);
			attach(data, key);

			return identifier;
		}
	}

	private static void attach(SkinData data, String key) {
		ByteBuffer buffer = data.getData();

		data.addListener(removed -> {
			if (removed.getData() != buffer) {
				return;
			}

			Minecraft.getInstance().execute(() -> release(data, key));
		});
	}

	private static void release(SkinData owner, String key) {
		synchronized (LOCK) {
			if (key.equals(RESOLVED.get(owner))) {
				RESOLVED.remove(owner);
			}

			Entry entry = TEXTURES.get(key);

			if (entry == null) {
				return;
			}

			entry.users--;

			if (entry.users > 0) {
				return;
			}

			TEXTURES.remove(key, entry);
			Minecraft.getInstance().getTextureManager().release(entry.identifier);
		}
	}

	private static Identifier register(SkinData data) {
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

		Identifier identifier = Identifier.fromNamespaceAndPath(OfflineSkins.MOD_ID, "textures/generated/" + UUID.randomUUID());

		try {
			Minecraft.getInstance().getTextureManager().register(identifier, new DynamicTexture(identifier::toString, fixed));
		} catch (Exception e) {
			fixed.close();

			return null;
		}

		return identifier;
	}

	private static final class Entry {
		private final Identifier identifier;
		private int users = 1;

		private Entry(Identifier identifier) {
			this.identifier = identifier;
		}
	}
}
