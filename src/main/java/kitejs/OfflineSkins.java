package kitejs;

import net.fabricmc.api.ClientModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OfflineSkins implements ClientModInitializer {
	public static final String MOD_ID = "offlineskins";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		LOGGER.info("OfflineSkins 已加载");
	}
}
