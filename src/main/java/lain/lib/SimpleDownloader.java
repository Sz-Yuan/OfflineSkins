package lain.lib;

import java.io.IOException;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Predicate;

// 简单异步下载：可选代理、重试、摘要与临时文件
public final class SimpleDownloader {

    private SimpleDownloader() {
        throw new Error("NoInstance");
    }

    private static Optional<URLConnection> connect(URL url, Proxy proxy, Consumer<URLConnection> preConnect, Consumer<Throwable> onException) {
        try {
            URLConnection conn = proxy == null ? url.openConnection() : url.openConnection(proxy);
            if (preConnect != null)
                preConnect.accept(conn);
            conn.connect();
            return Optional.of(conn);
        } catch (Throwable e) {
            if (onException != null)
                onException.accept(e);
            return Optional.empty();
        }
    }

    // 删除临时文件；不关心是否真正删成
    private static void deleteQuietly(Path path, Consumer<Throwable> onException) {
        try {
            Files.deleteIfExists(path);
        } catch (Throwable e) {
            if (onException != null)
                onException.accept(e);
        }
    }

    private static <T extends Throwable> Consumer<T> deleteOnExceptionDecor(Path path, Consumer<T> onException) {
        Consumer<T> deleteOnException = ignored -> deleteQuietly(path, SimpleDownloader::rethrowIfNonIOException);
        return onException == null ? deleteOnException : deleteOnException.andThen(onException);
    }

    private static <T> Predicate<T> deleteOnFalseDecor(Path path, Predicate<T> predicate) {
        if (predicate == null)
            return null;
        return t -> {
            if (predicate.test(t))
                return true;
            deleteQuietly(path, SimpleDownloader::rethrowIfNonIOException);
            return false;
        };
    }

    private static Optional<Path> download(Path local, URLConnection conn, MessageDigest digest, Predicate<URLConnection> shouldTransfer, Consumer<Throwable> onException) {
        try {
            if (shouldTransfer != null && !shouldTransfer.test(conn))
                return Optional.empty();
            try (FileChannel channel = FileChannel.open(local, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                channel.transferFrom(Channels.newChannel(digest == null ? conn.getInputStream() : new DigestInputStream(conn.getInputStream(), digest)), 0L, Long.MAX_VALUE);
                return Optional.of(local);
            }
        } catch (Throwable e) {
            if (digest != null)
                digest.reset();
            if (onException != null)
                onException.accept(e);
            return Optional.empty();
        }
    }

    // 将资源地址转为 URL；非法地址时回调并返回空
    private static Optional<URL> resource(String resource, Consumer<Throwable> onException) {
        try {
            return Optional.of(URI.create(resource).toURL());
        } catch (Throwable e) {
            if (onException != null)
                onException.accept(e);
            return Optional.empty();
        }
    }

    private static void rethrowIfNonIOException(Throwable throwable) {
        if (throwable instanceof IOException)
            return;
        Retries.rethrow(throwable);
    }

    private static void runAsync(Runnable runnable, Executor executor) {
        if (executor == null)
            CompletableFuture.runAsync(runnable).exceptionally(ignored -> null);
        else
            CompletableFuture.runAsync(runnable, executor).exceptionally(ignored -> null);
    }

    // 重试前固定等待 1 秒
    private static void sleep(Consumer<Throwable> onException) {
        try {
            Thread.sleep(1000L);
        } catch (Throwable e) {
            if (onException != null)
                onException.accept(e);
        }
    }

    // 异步下载入口：连接 → 临时文件 → 写入；失败 complete 空 Optional
    public static CompletableFuture<Optional<Path>> start(String resource, Path tempDir, Proxy proxy, int maxRetries, MessageDigest digest, Executor executor, Consumer<Thread> preExecute, Consumer<URLConnection> preConnect, Predicate<URLConnection> shouldTransfer) {
        Objects.requireNonNull(resource);
        CompletableFuture<Optional<Path>> future = new CompletableFuture<>();
        runAsync(() -> {
            try {
                if (!future.isDone()) {
                    if (preExecute != null)
                        preExecute.accept(Thread.currentThread());
                    resource(resource, future::completeExceptionally).ifPresent(remote -> Retries
                            .retrying(
                                    () -> {
                                        if (future.isDone())
                                            return;
                                        connect(remote, proxy, preConnect, Retries::rethrow).ifPresent(conn -> {
                                            Optional<Path> localOpt = tempFile(tempDir, future::completeExceptionally);
                                            if (localOpt.isEmpty())
                                                return;
                                            Path local = localOpt.get();
                                            Optional<Path> out = download(
                                                    local,
                                                    conn,
                                                    digest,
                                                    deleteOnFalseDecor(local, shouldTransfer),
                                                    deleteOnExceptionDecor(local, Retries::rethrow));
                                            if (out.isPresent())
                                                future.complete(out);
                                        });
                                    },
                                    IOException.class::isInstance,
                                    ignored -> sleep(Retries::rethrow),
                                    maxRetries)
                            .toRunnable(future::completeExceptionally)
                            .run());
                }
            } finally {
                if (!future.isDone())
                    future.complete(Optional.empty());
            }
        }, executor);
        return future;
    }

    private static Optional<Path> tempFile(Path tempDir, Consumer<Throwable> onException) {
        try {
            return Optional.of(tempDir == null ? Files.createTempFile(null, null) : Files.createTempFile(tempDir, null, null));
        } catch (Throwable e) {
            if (onException != null)
                onException.accept(e);
            return Optional.empty();
        }
    }

}
