package kitejs.skin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;

import kitejs.OfflineSkins;
import kitejs.data.AggregateSkinData;
import kitejs.data.SkinData;
import kitejs.profile.PlayerProfile;

public class SkinService {
	private static final SkinData NOT_READY = new SkinData();

	private final List<SkinProvider> providers = new CopyOnWriteArrayList<>();
	private final Cache<PlayerProfile, AggregateSkinData> cache;

	public SkinService() {
		RemovalListener<PlayerProfile, AggregateSkinData> onRemoval = notification -> {
			AggregateSkinData data = notification.getValue();

			if (data != null) {
				data.onRemoval();
			}
		};

		cache = CacheBuilder.newBuilder()
			.expireAfterAccess(Duration.ofSeconds(15L))
			.removalListener(onRemoval)
			.build();
	}

	public SkinData getSkin(PlayerProfile profile) {
		if (profile == null) {
			return NOT_READY;
		}

		try {
			return cache.get(profile, () -> createAggregate(profile));
		} catch (Exception e) {
			OfflineSkins.LOGGER.warn("皮肤服务加载档案失败", e);
			return NOT_READY;
		}
	}

	public void addProvider(SkinProvider provider) {
		if (provider == null) {
			return;
		}

		providers.add(provider);
	}

	public void clearProviders() {
		providers.clear();
		releaseAll();
	}

	public void cleanUp() {
		cache.cleanUp();
	}

	private void releaseAll() {
		for (AggregateSkinData data : cache.asMap().values()) {
			data.onRemoval();
		}

		cache.invalidateAll();
	}

	private AggregateSkinData createAggregate(PlayerProfile profile) {
		List<SkinData> collected = new ArrayList<>();

		for (SkinProvider provider : providers) {
			try {
				SkinData data = provider.getSkin(profile);

				if (data != null) {
					collected.add(data);
				}
			} catch (Exception e) {
				OfflineSkins.LOGGER.warn("皮肤提供者执行失败", e);
			}
		}

		AggregateSkinData aggregate = new AggregateSkinData();
		aggregate.set(collected);

		return aggregate;
	}
}
