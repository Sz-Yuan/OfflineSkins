package kitejs.skin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
	private static final long RETRY_BASE_DELAY = 5_000L;
	private static final int RETRY_MAX_SHIFT = 5;

	private final List<SkinProvider> providers = new CopyOnWriteArrayList<>();
	private final Map<PlayerProfile, Retry> retries = new ConcurrentHashMap<>();
	private final Cache<PlayerProfile, AggregateSkinData> cache;

	public SkinService() {
		RemovalListener<PlayerProfile, AggregateSkinData> onRemoval = notification -> {
			PlayerProfile profile = notification.getKey();

			if (profile != null) {
				retries.remove(profile);
			}

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
			AggregateSkinData aggregate = cache.get(profile, () -> createAggregate(profile));
			retry(profile, aggregate);

			return aggregate;
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

	private void retry(PlayerProfile profile, AggregateSkinData aggregate) {
		if (aggregate.isDataReady()) {
			retries.remove(profile);

			return;
		}

		long now = System.currentTimeMillis();
		Retry retry = retries.get(profile);

		if (retry != null && now < retry.nextAttemptAt()) {
			return;
		}

		int attempts = retry == null ? 1 : retry.attempts() + 1;
		long delay = RETRY_BASE_DELAY << Math.min(attempts - 1, RETRY_MAX_SHIFT);
		retries.put(profile, new Retry(attempts, now + delay));

		aggregate.set(loadMembers(profile));
	}

	private void releaseAll() {
		for (AggregateSkinData data : cache.asMap().values()) {
			data.onRemoval();
		}

		cache.invalidateAll();
	}

	private AggregateSkinData createAggregate(PlayerProfile profile) {
		AggregateSkinData aggregate = new AggregateSkinData();
		aggregate.set(loadMembers(profile));

		return aggregate;
	}

	private List<SkinData> loadMembers(PlayerProfile profile) {
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

		return collected;
	}

	private record Retry(int attempts, long nextAttemptAt) {
	}
}
