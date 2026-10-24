package com.lima.boxier;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.cef.CefApp;
import org.cef.CefSettings;

import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.impl.progress.ConsoleProgressHandler;
import net.neoforged.fml.loading.FMLPaths;

public final class BrowserRuntime {
    static {
        System.setProperty("java.awt.headless", "false");
    }

    private static final Object LOCK = new Object();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Boxier-Browser");
        thread.setDaemon(true);
        return thread;
    });

    private static CompletableFuture<CefApp> initialization;
    private static volatile CefApp app;
    private static volatile Throwable failure;

    private BrowserRuntime() {
    }

    public static CompletableFuture<CefApp> initialize() {
        synchronized (LOCK) {
            if (app != null) {
                return CompletableFuture.completedFuture(app);
            }
            if (failure != null) {
                return CompletableFuture.failedFuture(failure);
            }
            if (initialization == null) {
                initialization = CompletableFuture.supplyAsync(BrowserRuntime::build, EXECUTOR);
            }
            return initialization;
        }
    }

    private static CefApp build() {
        try {
            CefAppBuilder builder = new CefAppBuilder();
            builder.setInstallDir(FMLPaths.GAMEDIR.get().resolve("boxier-browser").toFile());
            builder.setProgressHandler(new ConsoleProgressHandler());
            builder.addJcefArgs("--disable-gpu");
            builder.addJcefArgs("--disable-dev-shm-usage");
            builder.addJcefArgs("--no-sandbox");
            builder.addJcefArgs("--autoplay-policy=no-user-gesture-required");
            builder.addJcefArgs("--mute-audio");
            builder.getCefSettings().windowless_rendering_enabled = false;
            builder.getCefSettings().log_severity = CefSettings.LogSeverity.LOGSEVERITY_INFO;
            builder.getCefSettings().log_file = FMLPaths.GAMEDIR.get().resolve("boxier-browser.log").toString();

            CefApp result = builder.build();
            app = result;
            Boxier.LOGGER.info("Browser runtime initialized");
            return result;
        } catch (Throwable throwable) {
            failure = throwable;
            Boxier.LOGGER.error("Failed to initialize browser runtime", throwable);
            throw new RuntimeException(throwable);
        }
    }

    public static boolean isReady() {
        return app != null;
    }

    public static Throwable getFailure() {
        return failure;
    }

    public static ExecutorService executor() {
        return EXECUTOR;
    }

    public static void shutdown() {
        CefApp current = app;
        app = null;
        initialization = null;
        if (current != null) {
            try {
                current.dispose();
            } catch (Exception exception) {
                Boxier.LOGGER.warn("Failed to dispose browser runtime", exception);
            }
        }
    }
}
