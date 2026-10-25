package com.lima.boxier;

import java.awt.Component;
import java.awt.Window;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefDevToolsClient;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefMenuModel;
import org.cef.handler.CefContextMenuHandlerAdapter;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefMessageRouterHandlerAdapter;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class BrowserSession implements AutoCloseable {
    private static final int SCREENCAST_MAX_DIMENSION = 960;
    private static final int SCREENCAST_QUALITY = 65;
    private static final int SCREENCAST_INTERVAL_MS = 33;

    public record Frame(byte[] jpeg, int width, int height, long timestamp) {
    }

    public record AudioChunk(short[] samples, int sampleRate, int channels) {
    }

    private final CefApp app;
    private volatile int width;
    private volatile int height;
    private final CountDownLatch firstFrame = new CountDownLatch(1);
    private final AtomicInteger frameCount = new AtomicInteger();
    private final AtomicInteger audioChunkCount = new AtomicInteger();
    private final AtomicReference<String> title = new AtomicReference<>("");
    private final AtomicReference<String> currentUrl = new AtomicReference<>("");

    private CefClient client;
    private CefBrowser browser;
    private CefDevToolsClient devTools;
    private CefMessageRouter router;
    private JFrame frame;
    private volatile Consumer<Frame> frameConsumer;
    private volatile Consumer<AudioChunk> audioConsumer;
    private volatile Frame lastFrame;
    private volatile long lastScreencastAt;

    private BrowserSession(CefApp app, int width, int height) {
        this.app = app;
        this.width = width;
        this.height = height;
    }

    public static CompletableFuture<BrowserSession> open(String url, int width, int height) {
        return BrowserRuntime.initialize().thenApplyAsync(
                app -> new BrowserSession(app, width, height).start(url),
                BrowserRuntime.executor());
    }

    private BrowserSession start(String url) {
        try {
            this.client = this.app.createClient();
            this.client.addContextMenuHandler(new CefContextMenuHandlerAdapter() {
                @Override
                public void onBeforeContextMenu(CefBrowser browser, CefFrame frame, CefContextMenuParams params,
                        CefMenuModel model) {
                    model.clear();
                }
            });
            this.client.addDisplayHandler(new CefDisplayHandlerAdapter() {
                @Override
                public void onTitleChange(CefBrowser browser, String newTitle) {
                    title.set(newTitle);
                }

                @Override
                public boolean onConsoleMessage(CefBrowser browser, CefSettings.LogSeverity level, String message,
                        String source, int line) {
                    if (message != null && message.startsWith("[Boxier]")) {
                        Boxier.LOGGER.info("Browser JS: {}", message);
                        return true;
                    }
                    return false;
                }
            });
            this.client.addLoadHandler(new CefLoadHandlerAdapter() {
                @Override
                public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
                    if (frame.isMain()) {
                        currentUrl.set(frame.getURL());
                        Boxier.LOGGER.debug("Browser loaded {} (status {})", frame.getURL(), httpStatusCode);
                    }
                }

                @Override
                public void onLoadError(CefBrowser browser, CefFrame frame, CefLoadHandler.ErrorCode errorCode,
                        String errorText, String failedUrl) {
                    Boxier.LOGGER.warn("Browser load error {} {} {}", errorCode, errorText, failedUrl);
                }
            });

            this.browser = this.client.createBrowser("about:blank", false, false);
            SwingUtilities.invokeAndWait(() -> {
                this.frame = new JFrame("Boxier Browser");
                this.frame.setUndecorated(true);
                this.frame.setType(Window.Type.UTILITY);
                this.frame.setSize(this.width, this.height);
                this.frame.setLocation(-10000, -10000);
                Component component = this.browser.getUIComponent();
                this.frame.add(component);
                this.frame.setVisible(true);
            });

            this.browser.createImmediately();
            Thread.sleep(500);

            this.devTools = this.browser.getDevToolsClient();
            if (this.devTools == null) {
                throw new IllegalStateException("DevTools client is unavailable");
            }
            this.devTools.addEventListener(this::onDevToolsEvent);
            this.devTools.executeDevToolsMethod("Page.enable").join();

            this.router = CefMessageRouter.create(new CefMessageRouterHandlerAdapter() {
                @Override
                public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request,
                        boolean persistent, CefQueryCallback callback) {
                    if (!request.startsWith("audio:")) {
                        return false;
                    }
                    handleAudio(request);
                    callback.success("");
                    return true;
                }
            });
            this.client.addMessageRouter(this.router);
            injectAudioHook();

            int maxWidth = Math.min(this.width, SCREENCAST_MAX_DIMENSION);
            int maxHeight = Math.min(this.height, SCREENCAST_MAX_DIMENSION);
            this.devTools.executeDevToolsMethod("Page.startScreencast",
                    "{\"format\":\"jpeg\",\"quality\":" + SCREENCAST_QUALITY + ",\"maxWidth\":" + maxWidth
                            + ",\"maxHeight\":" + maxHeight + ",\"everyNthFrame\":1}").join();

            if (url != null && !url.isBlank()) {
                this.browser.loadURL(url);
            }

            if (!this.firstFrame.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the first browser frame");
            }
            return this;
        } catch (Exception exception) {
            close();
            throw new IllegalStateException("Failed to start browser session", exception);
        }
    }

    private void onDevToolsEvent(String method, String parameters) {
        if (!"Page.screencastFrame".equals(method)) {
            return;
        }
        try {
            JsonObject json = JsonParser.parseString(parameters).getAsJsonObject();
            int sessionId = json.get("sessionId").getAsInt();
            long now = System.currentTimeMillis();
            if (now - this.lastScreencastAt >= SCREENCAST_INTERVAL_MS) {
                this.lastScreencastAt = now;
                String data = json.get("data").getAsString();
                byte[] jpeg = Base64.getDecoder().decode(data);
                int frameWidth = this.width;
                int frameHeight = this.height;
                if (json.has("metadata")) {
                    JsonObject metadata = json.getAsJsonObject("metadata");
                    if (metadata.has("deviceWidth")) {
                        frameWidth = Math.round(metadata.get("deviceWidth").getAsFloat());
                    }
                    if (metadata.has("deviceHeight")) {
                        frameHeight = Math.round(metadata.get("deviceHeight").getAsFloat());
                    }
                }
                Frame frame = new Frame(jpeg, frameWidth, frameHeight, now);
                this.lastFrame = frame;
                Consumer<Frame> consumer = this.frameConsumer;
                if (consumer != null) {
                    consumer.accept(frame);
                }
                this.frameCount.incrementAndGet();
            }
            this.firstFrame.countDown();
            this.devTools.executeDevToolsMethod("Page.screencastFrameAck", "{\"sessionId\":" + sessionId + "}");
        } catch (Exception exception) {
            Boxier.LOGGER.warn("Failed to handle browser screencast frame", exception);
        }
    }

    private void injectAudioHook() {
        InputStream stream = BrowserSession.class.getResourceAsStream("/assets/boxier/browser/audio-hook.js");
        if (stream == null) {
            Boxier.LOGGER.warn("Browser audio hook resource is missing");
            return;
        }
        try (InputStream input = stream) {
            String script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            String parameters = "{\"source\":" + new Gson().toJson(script) + "}";
            this.devTools.executeDevToolsMethod("Page.addScriptToEvaluateOnNewDocument", parameters).join();
        } catch (Exception exception) {
            Boxier.LOGGER.warn("Failed to inject browser audio hook", exception);
        }
    }

    private void handleAudio(String request) {
        Consumer<AudioChunk> consumer = this.audioConsumer;
        try {
            int rateEnd = request.indexOf(':', "audio:".length());
            if (rateEnd < 0) {
                return;
            }
            int channelsEnd = request.indexOf(':', rateEnd + 1);
            if (channelsEnd < 0) {
                return;
            }
            int sampleRate = Integer.parseInt(request.substring("audio:".length(), rateEnd));
            int channels = Integer.parseInt(request.substring(rateEnd + 1, channelsEnd));
            if (channels != 1 && channels != 2) {
                return;
            }
            byte[] pcm = Base64.getDecoder().decode(request.substring(channelsEnd + 1));
            ShortBuffer samples = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
            short[] values = new short[samples.remaining()];
            samples.get(values);
            int chunk = this.audioChunkCount.incrementAndGet();
            if (chunk <= 5 || chunk % 100 == 0) {
                int peak = 0;
                for (short value : values) {
                    peak = Math.max(peak, Math.abs((int) value));
                }
                Boxier.LOGGER.debug("Browser audio chunk={} peak={} samples={} rate={} channels={} consumer={}", chunk,
                        peak, values.length, sampleRate, channels, consumer != null);
            }
            if (consumer != null) {
                consumer.accept(new AudioChunk(values, sampleRate, channels));
            }
        } catch (Exception exception) {
            Boxier.LOGGER.warn("Failed to decode browser audio", exception);
        }
    }

    public void setFrameConsumer(Consumer<Frame> consumer) {
        this.frameConsumer = consumer;
        Frame frame = this.lastFrame;
        if (consumer != null && frame != null) {
            consumer.accept(frame);
        }
    }

    public void setAudioConsumer(Consumer<AudioChunk> consumer) {
        this.audioConsumer = consumer;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public int getFrameCount() {
        return this.frameCount.get();
    }

    public String getTitle() {
        return this.title.get();
    }

    public String getCurrentUrl() {
        return this.currentUrl.get();
    }

    public CefBrowser getBrowser() {
        return this.browser;
    }

    public CefDevToolsClient getDevTools() {
        return this.devTools;
    }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        SwingUtilities.invokeLater(() -> {
            JFrame currentFrame = this.frame;
            if (currentFrame != null) {
                currentFrame.setSize(width, height);
                currentFrame.validate();
            }
            if (this.browser != null) {
                Component component = this.browser.getUIComponent();
                component.setSize(width, height);
            }
        });
    }

    public void loadUrl(String url) {
        if (this.browser != null) {
            this.browser.loadURL(url);
        }
    }

    public void goBack() {
        if (this.browser != null) {
            this.browser.goBack();
        }
    }

    public void goForward() {
        if (this.browser != null) {
            this.browser.goForward();
        }
    }

    public void reload() {
        if (this.browser != null) {
            this.browser.reload();
        }
    }

    public void executeDevTools(String method, String parameters) {
        if (this.devTools != null) {
            this.devTools.executeDevToolsMethod(method, parameters);
        }
    }

    @Override
    public void close() {
        if (this.client != null && this.router != null) {
            try {
                this.client.removeMessageRouter(this.router);
            } catch (Exception exception) {
                Boxier.LOGGER.warn("Failed to remove browser audio router", exception);
            }
            this.router.dispose();
            this.router = null;
        }
        if (this.devTools != null) {
            try {
                this.devTools.executeDevToolsMethod("Page.stopScreencast");
            } catch (Exception exception) {
                Boxier.LOGGER.warn("Failed to stop browser screencast", exception);
            }
            try {
                this.devTools.close();
            } catch (Exception exception) {
                Boxier.LOGGER.warn("Failed to close browser DevTools", exception);
            }
            this.devTools = null;
        }
        if (this.browser != null) {
            try {
                this.browser.close(true);
            } catch (Exception exception) {
                Boxier.LOGGER.warn("Failed to close browser", exception);
            }
            this.browser = null;
        }
        if (this.client != null) {
            try {
                this.client.dispose();
            } catch (Exception exception) {
                Boxier.LOGGER.warn("Failed to dispose browser client", exception);
            }
            this.client = null;
        }
        JFrame currentFrame = this.frame;
        this.frame = null;
        if (currentFrame != null) {
            SwingUtilities.invokeLater(currentFrame::dispose);
        }
    }
}
