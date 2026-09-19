package lain.mods.skins.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.mojang.authlib.GameProfile;
import lain.lib.Retries;
import lain.lib.SharedPool;
import lain.lib.SimpleDownloader;
import lain.mods.skins.impl.fabric.MinecraftUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.function.Supplier;

// 共享工具：离线判定、下载、文件读取等
public class Shared {

    // 占位档案：表示无效/失败结果
    public static final GameProfile DUMMY = new GameProfile(UUID.fromString("ae9460f5-bf72-468e-89b6-4eead59001ad"), "");

    // 离线 UUID 判定结果缓存
    private static final Cache<UUID, Boolean> offlines = CacheBuilder.newBuilder().weakKeys().build();

    // 执行 callable，异常时返回 defaultValue 并交给 consumer
    public static <T> T call(Callable<T> callable, T defaultValue, Consumer<Throwable> consumer) {
        if (callable == null)
            return defaultValue;
        try {
            return callable.call();
        } catch (Throwable t) {
            if (consumer != null)
                consumer.accept(t);
            return defaultValue;
        }
    }

    public static byte[] readFile(File file, byte[] defaultContents, Consumer<Throwable> consumer) {
        if (file == null)
            return defaultContents;
        return call(() -> {
            try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                channel.transferTo(0L, Long.MAX_VALUE, Channels.newChannel(baos));
                return baos.toByteArray();
            }
        }, defaultContents, consumer);
    }

    // 下载皮肤/披风到临时文件并读入内存
    public static CompletableFuture<Optional<byte[]>> downloadSkin(String resource, Executor executor) {
        return SimpleDownloader
                .start(encodeURL(resource), null, MinecraftUtils.getProxy(), 2, null, executor, null, Shared::preConnect, Shared::stopIfHttpClientError)
                .thenApply(Shared::readAndDelete);
    }

    // URL 编码失败时回退原串
    private static String encodeURL(String url) {
        try {
            return new URI(url).toASCIIString();
        } catch (NullPointerException | URISyntaxException e) {
            return url;
        }
    }

    public static boolean isBlank(CharSequence cs) {
        if (cs == null || cs.isEmpty())
            return true;
        for (int i = 0; i < cs.length(); i++)
            if (!Character.isWhitespace(cs.charAt(i)))
                return false;
        return true;
    }

    // UUID == OfflinePlayer:<name> 的 nameUUID 时视为离线档案
    // 不完整档案也当作离线，但不写入缓存（后续可能被补全）
    public static boolean isOfflinePlayer(UUID id, String name) {
        if (id == null || isBlank(name))
            return true;
        try {
            return offlines.get(id, () -> UUID
                    .nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8))
                    .equals(id));
        } catch (Throwable t) {
            return true;
        }
    }

    private static void preConnect(URLConnection conn) {
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        conn.setUseCaches(true);
        conn.setDoInput(true);
        conn.setDoOutput(false);
    }

    // 读取临时文件内容并删除文件
    private static Optional<byte[]> readAndDelete(Optional<Path> path) {
        try (FileChannel channel = FileChannel.open(path.orElseThrow(FileNotFoundException::new), StandardOpenOption.READ, StandardOpenOption.DELETE_ON_CLOSE); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            channel.transferTo(0L, Long.MAX_VALUE, Channels.newChannel(baos));
            return Optional.of(baos.toByteArray());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }

    // HTTP 4xx 时停止下载重试
    private static boolean stopIfHttpClientError(URLConnection conn) {
        if (conn instanceof HttpURLConnection)
            try {
                if (((HttpURLConnection) conn).getResponseCode() / 100 == 4)
                    return false;
            } catch (IOException e) {
                Retries.rethrow(e);
            }
        return true;
    }

    public static <T> ListenableFuture<T> submitTask(Callable<T> callable) {
        ListenableFutureTask<T> future = ListenableFutureTask.create(callable);
        SharedPool.execute(future);
        return future;
    }

    private interface SupplierBlocker<T> extends Supplier<T>, ForkJoinPool.ManagedBlocker {
    }

}
