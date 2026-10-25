package com.lima.boxier;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.Gson;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BrowserSessionManager {
    private static final double VIEW_RADIUS = 64.0;
    private static final double CONTROL_RADIUS = 32.0;
    private static final double AUDIO_RADIUS = 64.0;
    private static final int VIEWPORT_MAX_DIMENSION = 1280;
    private static final Gson GSON = new Gson();

    private static final Map<SessionKey, Entry> SESSIONS = new ConcurrentHashMap<>();
    private static final ExecutorService ENCODER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Boxier-Browser-Encoder");
        thread.setDaemon(true);
        return thread;
    });

    private static final ExecutorService AUDIO_ENCODER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Boxier-Browser-Audio");
        thread.setDaemon(true);
        return thread;
    });

    private static final Object TEST_LOCK = new Object();
    private static CompletableFuture<BrowserSession> testSession;

    private BrowserSessionManager() {
    }

    public static void requestControl(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!canUse(level, pos, player, true)) {
            return;
        }

        SessionKey key = new SessionKey(level.dimension(), pos.immutable());
        Entry entry = SESSIONS.computeIfAbsent(key, ignored -> new Entry(level, pos.immutable()));

        synchronized (entry) {
            if (entry.controller != null && !entry.controller.equals(player.getUUID())) {
                Boxier.LOGGER.info("Browser control denied to {} at {} (in use)", player.getName().getString(), pos);
                sendControlResponse(player, pos, false, "This browser is already in use", entry.url);
                return;
            }

            entry.controller = player.getUUID();
            Boxier.LOGGER.info("Browser control granted to {} at {}", player.getName().getString(), pos);
            if (entry.session == null) {
                start(entry);
            }
            sendControlResponse(player, pos, true, "", entry.url);
        }
    }

    public static void release(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Entry entry = SESSIONS.get(new SessionKey(level.dimension(), pos));
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            if (player.getUUID().equals(entry.controller)) {
                entry.controller = null;
            }
        }
    }

    public static void resize(ServerLevel level, BlockPos pos, int displayWidth, int displayHeight) {
        Entry entry = SESSIONS.get(new SessionKey(level.dimension(), pos));
        if (entry == null) {
            return;
        }
        int[] viewport = viewportFor(level, pos);
        synchronized (entry) {
            CompletableFuture<BrowserSession> future = entry.session;
            if (future != null) {
                future.thenAccept(session -> session.resize(viewport[0], viewport[1]));
            }
        }
    }

    public static void remove(ServerLevel level, BlockPos pos) {
        Entry entry = SESSIONS.remove(new SessionKey(level.dimension(), pos));
        if (entry != null) {
            synchronized (entry) {
                closeSession(entry);
            }
        }
    }

    public static void mouse(ServerPlayer player, BrowserMousePayload payload) {
        Entry entry = controlledEntry(player, payload.pos());
        if (entry == null) {
            return;
        }
        BrowserSession session = readySession(entry);
        if (session == null) {
            return;
        }

        float normalizedX = clamp(payload.x());
        float normalizedY = clamp(payload.y());
        int x = Math.round(normalizedX * session.getWidth());
        int y = Math.round(normalizedY * session.getHeight());

        String json = switch (payload.action()) {
            case BrowserMousePayload.MOVE -> "{\"type\":\"mouseMoved\",\"x\":" + x + ",\"y\":" + y
                    + ",\"button\":\"none\",\"modifiers\":" + payload.modifiers() + "}";
            case BrowserMousePayload.DOWN -> mouseButton("mousePressed", x, y, payload);
            case BrowserMousePayload.UP -> mouseButton("mouseReleased", x, y, payload);
            case BrowserMousePayload.SCROLL -> "{\"type\":\"mouseWheel\",\"x\":" + x + ",\"y\":" + y
                    + ",\"button\":\"none\",\"deltaX\":0,\"deltaY\":" + payload.scroll()
                    + ",\"modifiers\":" + payload.modifiers() + "}";
            default -> null;
        };

        if (json != null) {
            session.executeDevTools("Input.dispatchMouseEvent", json);
        }

        PacketDistributor.sendToPlayersNear(entry.level, player, entry.pos.getX() + 0.5, entry.pos.getY() + 0.5,
                entry.pos.getZ() + 0.5, VIEW_RADIUS,
                new BrowserCursorPayload(entry.pos, normalizedX, normalizedY));
    }

    public static void action(ServerPlayer player, BrowserActionPayload payload) {
        Entry entry = controlledEntry(player, payload.pos());
        if (entry == null) {
            return;
        }
        BrowserSession session = readySession(entry);
        if (session == null) {
            return;
        }

        switch (payload.action()) {
            case BrowserActionPayload.NAVIGATE -> {
                String url = normalizeUrl(payload.url());
                if (url != null) {
                    Boxier.LOGGER.info("Browser navigate to {} at {}", url, payload.pos());
                    entry.url = url;
                    session.loadUrl(url);
                } else {
                    Boxier.LOGGER.info("Browser navigate rejected input '{}' at {}", payload.url(), payload.pos());
                }
            }
            case BrowserActionPayload.BACK -> session.goBack();
            case BrowserActionPayload.FORWARD -> session.goForward();
            case BrowserActionPayload.RELOAD -> session.reload();
            default -> {
            }
        }
    }

    public static void key(ServerPlayer player, BrowserKeyPayload payload) {
        Entry entry = controlledEntry(player, payload.pos());
        if (entry == null) {
            return;
        }
        BrowserSession session = readySession(entry);
        if (session == null) {
            return;
        }

        if (payload.action() == BrowserKeyPayload.TEXT) {
            if (!payload.text().isEmpty()) {
                session.executeDevTools("Input.insertText", "{\"text\":" + GSON.toJson(payload.text()) + "}");
            }
            return;
        }

        String type = payload.action() == BrowserKeyPayload.DOWN ? "keyDown" : "keyUp";
        StringBuilder json = new StringBuilder()
                .append("{\"type\":\"").append(type)
                .append("\",\"key\":").append(GSON.toJson(payload.key()))
                .append(",\"code\":").append(GSON.toJson(payload.code()))
                .append(",\"windowsVirtualKeyCode\":").append(payload.keyCode())
                .append(",\"modifiers\":").append(payload.modifiers());
        if (payload.action() == BrowserKeyPayload.DOWN && !payload.text().isEmpty()) {
            json.append(",\"text\":").append(GSON.toJson(payload.text()));
        }
        json.append('}');
        session.executeDevTools("Input.dispatchKeyEvent", json.toString());
    }

    public static CompletableFuture<BrowserSession> openTest(String url, int width, int height) {
        synchronized (TEST_LOCK) {
            closeTestLocked();
            CompletableFuture<BrowserSession> future = BrowserSession.open(url, width, height);
            testSession = future;
            return future;
        }
    }

    public static void closeTest() {
        synchronized (TEST_LOCK) {
            closeTestLocked();
        }
    }

    private static void closeTestLocked() {
        if (testSession != null) {
            testSession.thenAccept(BrowserSession::close);
            testSession = null;
        }
    }

    public static void shutdown() {
        closeTest();
        for (Entry entry : SESSIONS.values()) {
            synchronized (entry) {
                closeSession(entry);
            }
        }
        SESSIONS.clear();
    }

    private static void start(Entry entry) {
        Boxier.LOGGER.info("Starting browser session at {}", entry.pos);
        int[] viewport = viewportFor(entry.level, entry.pos);
        entry.session = BrowserSession.open(entry.url, viewport[0], viewport[1]);
        entry.session.whenComplete((session, throwable) -> {
            if (throwable != null) {
                Boxier.LOGGER.error("Failed to start browser session at {}", entry.pos, throwable);
                return;
            }
            session.setFrameConsumer(frame -> stream(entry, frame));
            session.setAudioConsumer(chunk -> streamAudio(entry, chunk));
        });
    }

    private static void closeSession(Entry entry) {
        CompletableFuture<BrowserSession> future = entry.session;
        entry.session = null;
        if (future != null) {
            future.thenAccept(BrowserSession::close);
        }
        synchronized (entry) {
            BrowserOpusEncoder encoder = entry.audioEncoder;
            entry.audioEncoder = null;
            if (encoder != null) {
                AUDIO_ENCODER.execute(encoder::close);
            }
        }
    }

    private static int[] viewportFor(ServerLevel level, BlockPos pos) {
        int displayWidth = BrowserBlockEntity.DEFAULT_SIZE;
        int displayHeight = BrowserBlockEntity.DEFAULT_SIZE;
        if (level.getBlockEntity(pos) instanceof BrowserBlockEntity browser) {
            displayWidth = browser.getDisplayWidth();
            displayHeight = browser.getDisplayHeight();
        }

        double aspect = (double) displayWidth / displayHeight;
        int width = VIEWPORT_MAX_DIMENSION;
        int height = VIEWPORT_MAX_DIMENSION;
        if (aspect >= 1.0) {
            height = Math.max(1, (int) Math.round(VIEWPORT_MAX_DIMENSION / aspect));
        } else {
            width = Math.max(1, (int) Math.round(VIEWPORT_MAX_DIMENSION * aspect));
        }
        return new int[] { width, height };
    }

    private static void stream(Entry entry, BrowserSession.Frame frame) {
        if (entry.encoding) {
            return;
        }
        if (!hasObservers(entry)) {
            return;
        }

        entry.encoding = true;
        ENCODER.execute(() -> {
            try {
                int sequence = ++entry.sequence;
                PacketDistributor.sendToPlayersNear(entry.level, null, entry.pos.getX() + 0.5,
                        entry.pos.getY() + 0.5, entry.pos.getZ() + 0.5, VIEW_RADIUS,
                        new BrowserFramePayload(entry.pos, sequence, frame.width(), frame.height(), frame.jpeg()));
            } catch (Exception exception) {
                Boxier.LOGGER.warn("Failed to send browser frame", exception);
            } finally {
                entry.encoding = false;
            }
        });
    }

    private static void streamAudio(Entry entry, BrowserSession.AudioChunk chunk) {
        if (!hasObservers(entry, AUDIO_RADIUS)) {
            return;
        }
        AUDIO_ENCODER.execute(() -> {
            BrowserOpusEncoder encoder;
            synchronized (entry) {
                if (entry.audioEncoder == null || entry.audioEncoder.getChannels() != chunk.channels()) {
                    try {
                        if (entry.audioEncoder != null) {
                            entry.audioEncoder.close();
                        }
                        entry.audioEncoder = new BrowserOpusEncoder(chunk.channels());
                    } catch (Exception exception) {
                        Boxier.LOGGER.warn("Failed to create browser Opus encoder", exception);
                        entry.audioEncoder = null;
                        return;
                    }
                }
                encoder = entry.audioEncoder;
            }
            try {
                for (byte[] frame : encoder.encode(chunk.samples(), chunk.sampleRate())) {
                    int frameCount = ++entry.audioFrames;
                    if (frameCount <= 3 || frameCount % 100 == 0) {
                        Boxier.LOGGER.debug("Sending browser audio frame {} ({} bytes) at {}", frameCount, frame.length,
                                entry.pos);
                    }
                    PacketDistributor.sendToPlayersNear(entry.level, null, entry.pos.getX() + 0.5,
                            entry.pos.getY() + 0.5, entry.pos.getZ() + 0.5, AUDIO_RADIUS,
                            new BrowserAudioPayload(entry.pos, frame));
                }
            } catch (Exception exception) {
                Boxier.LOGGER.warn("Failed to encode browser audio", exception);
            }
        });
    }

    private static boolean hasObservers(Entry entry) {
        return hasObservers(entry, VIEW_RADIUS);
    }

    private static boolean hasObservers(Entry entry, double radius) {
        double radiusSquared = radius * radius;
        for (ServerPlayer player : entry.level.players()) {
            if (player.distanceToSqr(entry.pos.getX() + 0.5, entry.pos.getY() + 0.5,
                    entry.pos.getZ() + 0.5) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    private static Entry controlledEntry(ServerPlayer player, BlockPos pos) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        Entry entry = SESSIONS.get(new SessionKey(level.dimension(), pos));
        if (entry == null || !player.getUUID().equals(entry.controller)) {
            return null;
        }
        if (!canUse(level, pos, player, false)) {
            return null;
        }
        return entry;
    }

    private static BrowserSession readySession(Entry entry) {
        CompletableFuture<BrowserSession> future = entry.session;
        if (future == null || !future.isDone() || future.isCompletedExceptionally()) {
            return null;
        }
        return future.getNow(null);
    }

    private static boolean canUse(ServerLevel level, BlockPos pos, ServerPlayer player, boolean notify) {
        if (!(level.getBlockEntity(pos) instanceof BrowserBlockEntity)) {
            return false;
        }
        double distanceSquared = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (distanceSquared > CONTROL_RADIUS * CONTROL_RADIUS) {
            if (notify) {
                sendControlResponse(player, pos, false, "You are too far away", "");
            }
            return false;
        }
        return true;
    }

    private static void sendControlResponse(ServerPlayer player, BlockPos pos, boolean granted, String message,
            String url) {
        PacketDistributor.sendToPlayer(player, new BrowserControlResponsePayload(pos, granted, message, url));
    }

    private static String normalizeUrl(String input) {
        if (input == null) {
            return null;
        }
        String value = input.trim();
        if (value.isEmpty()) {
            return "about:blank";
        }
        if (value.equalsIgnoreCase("about:blank")) {
            return "about:blank";
        }

        String lower = value.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return isHttpUrl(value) ? value : searchUrl(value);
        }
        if (value.indexOf(' ') >= 0 || value.indexOf('.') < 0) {
            return searchUrl(value);
        }

        String candidate = "https://" + value;
        return isHttpUrl(candidate) ? candidate : searchUrl(value);
    }

    private static boolean isHttpUrl(String value) {
        try {
            java.net.URI uri = java.net.URI.create(value);
            String scheme = uri.getScheme();
            return scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    && uri.getHost() != null;
        } catch (Exception exception) {
            return false;
        }
    }

    private static String searchUrl(String value) {
        return "https://www.google.com/search?q="
                + java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String mouseButton(String type, int x, int y, BrowserMousePayload payload) {
        String button = switch (payload.button()) {
            case 1 -> "left";
            case 2 -> "middle";
            case 3 -> "right";
            default -> "left";
        };
        int buttons = 0;
        if ("mousePressed".equals(type)) {
            buttons = switch (payload.button()) {
                case 1 -> 1;
                case 3 -> 2;
                case 2 -> 4;
                default -> 0;
            };
        }
        return "{\"type\":\"" + type + "\",\"x\":" + x + ",\"y\":" + y + ",\"button\":\"" + button
                + "\",\"buttons\":" + buttons + ",\"clickCount\":1,\"modifiers\":" + payload.modifiers() + "}";
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private record SessionKey(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private static final class Entry {
        private final ServerLevel level;
        private final BlockPos pos;
        private CompletableFuture<BrowserSession> session;
        private UUID controller;
        private String url = "about:blank";
        private int sequence;
        private volatile boolean encoding;
        private int audioFrames;
        private BrowserOpusEncoder audioEncoder;

        private Entry(ServerLevel level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
        }
    }
}
