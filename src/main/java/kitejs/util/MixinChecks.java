package kitejs.util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;

public final class MixinChecks {
	private static final String HANDLER_PREFIX = "offlineskins$";
	private static final List<Check> CHECKS = List.of(
		new Check(PlayerInfo.class, "PlayerInfo#getSkin", "offlineskins.hook.playerInfo", "getSkin"),
		new Check(PlayerTabOverlay.class, "PlayerTabOverlay#extractRenderState", "offlineskins.hook.tabOverlay", "showHead"),
		new Check(SkullBlockRenderer.class, "SkullBlockRenderer#resolveSkullRenderType", "offlineskins.hook.skullRenderer", "resolveSkullRenderType"),
		new Check(PlayerSkinRenderCache.class, "PlayerSkinRenderCache#getOrDefault", "offlineskins.hook.skinRenderCache", "getOrDefault"),
		new Check(ClientMannequin.class, "ClientMannequin#getSkin", "offlineskins.hook.mannequin", "getSkin")
	);

	private MixinChecks() {
	}

	public static List<Result> audit() {
		List<Result> results = new ArrayList<>();

		for (Check check : CHECKS) {
			results.add(new Result(check.hook(), check.descriptionKey(), isApplied(check)));
		}

		return results;
	}

	private static boolean isApplied(Check check) {
		for (Method method : check.target().getDeclaredMethods()) {
			if (method.getName().contains(HANDLER_PREFIX + check.handler())) {
				return true;
			}
		}

		return false;
	}

	private record Check(Class<?> target, String hook, String descriptionKey, String handler) {
	}

	public record Result(String hook, String descriptionKey, boolean applied) {
	}
}
