package lain.mods.skins.providers;

import com.mojang.logging.LogUtils;
import lain.lib.SharedPool;
import lain.mods.skins.api.interfaces.IPlayerProfile;
import lain.mods.skins.api.interfaces.ISkin;
import lain.mods.skins.api.interfaces.ISkinProvider;
import lain.mods.skins.impl.Shared;
import lain.mods.skins.impl.SkinData;
import lain.mods.skins.impl.fabric.ImageUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

// 本地纹理：cachedImages/{skins|capes}/[uuid/]<名>.png
// 只要文件存在即可使用，不要求正版
public class UserManagedProvider implements ISkinProvider {

    private static final Logger LOGGER = LogUtils.getLogger();

    public enum Kind { SKIN, CAPE }

    private final Kind kind;
    private final File dirByName;
    private final File dirByUuid;
    private Function<ByteBuffer, ByteBuffer> filter;

    public UserManagedProvider(Path workDir, Kind kind) {
        this.kind = kind;
        String folder = kind == Kind.SKIN ? "skins" : "capes";
        dirByName = new File(workDir.toFile(), folder);
        dirByUuid = new File(dirByName, "uuid");
        // 创建缓存目录；失败不阻断启动，读取阶段会视为无本地皮肤
        try {
            Files.createDirectories(dirByUuid.toPath());
        } catch (IOException e) {
            // 不阻断启动；读取阶段会视为无本地皮肤
            LOGGER.error("无法创建本地皮肤目录 {}", dirByUuid, e);
        }
    }

    @Override
    public ISkin getSkin(IPlayerProfile profile) {
        SkinData skin = new SkinData();
        if (filter != null)
            skin.setSkinFilter(filter);
        SharedPool.execute(() -> {
            byte[] data = null;
            // 在线档案优先读 uuid 目录，再读用户名文件（离线名也适用）
            if (!Shared.isOfflinePlayer(profile.getPlayerID(), profile.getPlayerName()))
                data = read(dirByUuid, profile.getPlayerID().toString().replace("-", "") + ".png");
            if (data == null && !Shared.isBlank(profile.getPlayerName()))
                data = read(dirByName, profile.getPlayerName() + ".png");
            if (data != null)
                skin.put(data, kind == Kind.SKIN ? ImageUtils.judgeSkinType(data) : "cape");
        });
        return skin;
    }

    private static byte[] read(File dir, String file) {
        byte[] contents = Shared.readFile(new File(dir, file), null, null);
        return contents != null && ImageUtils.validateData(contents) ? contents : null;
    }

    public UserManagedProvider withFilter(Function<ByteBuffer, ByteBuffer> filter) {
        this.filter = filter;
        return this;
    }

}
