package kitejs.data;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import kitejs.OfflineSkins;

public class AggregateSkinData extends SkinData {
	private final AtomicReference<List<SkinData>> members = new AtomicReference<>(List.of());

	@Override
	public void put(byte[] bytes, String type) {
		throw new UnsupportedOperationException("Aggregate containers do not accept direct writes");
	}

	public void set(Collection<SkinData> newMembers) {
		List<SkinData> next = new ArrayList<>();

		if (newMembers != null) {
			for (SkinData member : newMembers) {
				if (member == null || member == this) {
					continue;
				}

				next.add(member);
			}
		}

		for (SkinData member : next) {
			for (Function<ByteBuffer, ByteBuffer> filter : filters()) {
				member.addFilter(filter);
			}

			for (Consumer<SkinData> listener : listeners()) {
				member.addListener(listener);
			}
		}

		List<SkinData> previous = members.getAndSet(List.copyOf(next));

		for (SkinData member : previous) {
			try {
				member.onRemoval();
			} catch (RuntimeException e) {
				OfflineSkins.LOGGER.warn("Failed to remove member while replacing aggregate members", e);
			}
		}
	}

	@Override
	public boolean isDataReady() {
		return firstReady() != null;
	}

	@Override
	public ByteBuffer getData() {
		SkinData member = firstReady();
		return member == null ? null : member.getData();
	}

	@Override
	public String getType() {
		SkinData member = firstReady();
		return member == null ? null : member.getType();
	}

	@Override
	public boolean isUnavailable() {
		List<SkinData> current = members.get();

		if (current.isEmpty()) {
			return false;
		}

		for (SkinData member : current) {
			if (!member.isUnavailable()) {
				return false;
			}
		}

		return true;
	}

	@Override
	public void addFilter(Function<ByteBuffer, ByteBuffer> filter) {
		if (filter == null) {
			return;
		}

		super.addFilter(filter);

		for (SkinData member : members.get()) {
			member.addFilter(filter);
		}
	}

	@Override
	public void addListener(Consumer<SkinData> listener) {
		if (listener == null) {
			return;
		}

		super.addListener(listener);

		for (SkinData member : members.get()) {
			member.addListener(listener);
		}
	}

	@Override
	public void onRemoval() {
		List<SkinData> previous = members.getAndSet(List.of());

		for (SkinData member : previous) {
			try {
				member.onRemoval();
			} catch (RuntimeException e) {
				OfflineSkins.LOGGER.warn("Failed to remove aggregate member", e);
			}
		}
	}

	private SkinData firstReady() {
		for (SkinData member : members.get()) {
			if (member.isDataReady()) {
				return member;
			}
		}

		return null;
	}
}
