package com.lima.boxier;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class BrowserAudioManager {
    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNELS = 2;
    private static final int FRAME_SAMPLES = 960;
    private static final int TARGET_FRAMES = 3;
    private static final int MAX_FRAMES = 12;
    private static final int LINE_FRAMES = 4;
    private static final float FADE_DISTANCE = 48.0F;

    private static final Map<Key, Channel> CHANNEL_MAP = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<Packet> INBOUND = new ConcurrentLinkedQueue<>();
    private static final Object START_LOCK = new Object();

    private static volatile boolean started;
    private static volatile boolean clearRequested;
    private static volatile float volume = 1.0F;
    private static volatile SourceDataLine line;

    private BrowserAudioManager() {
    }

    public static void setVolume(float value) {
        volume = Math.max(0.0F, Math.min(1.0F, value));
    }

    public static float getVolume() {
        return volume;
    }

    public static void enqueue(BlockPos pos, byte[] data) {
        INBOUND.offer(new Packet(pos.immutable(), data));
        ensureStarted();
    }

    public static void clear() {
        clearRequested = true;
        INBOUND.clear();
    }

    private static void ensureStarted() {
        synchronized (START_LOCK) {
            if (started) {
                return;
            }
            started = true;
            Thread thread = new Thread(BrowserAudioManager::run, "Boxier-Browser-Audio");
            thread.setDaemon(true);
            thread.start();
        }
    }

    private static void run() {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, CHANNELS, true, false);
        try {
            SourceDataLine newLine = AudioSystem.getSourceDataLine(format);
            newLine.open(format, FRAME_SAMPLES * CHANNELS * 2 * LINE_FRAMES);
            newLine.start();
            line = newLine;
        } catch (Exception exception) {
            Boxier.LOGGER.warn("Failed to open browser audio output", exception);
            return;
        }

        short[] mix = new short[FRAME_SAMPLES * CHANNELS];
        byte[] bytes = new byte[mix.length * 2];
        ByteBuffer output = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        while (true) {
            if (clearRequested) {
                clearRequested = false;
                clearChannels();
            }

            drainInbound();
            Level level = Minecraft.getInstance().level;
            if (level == null) {
                sleep(20);
                continue;
            }

            Arrays.fill(mix, (short) 0);
            for (Channel channel : CHANNEL_MAP.values()) {
                short[] frame = channel.pollFrame();
                if (frame == null) {
                    continue;
                }
                float gain = gain(level, channel.pos);
                if (gain <= 0.0001F) {
                    continue;
                }
                for (int i = 0; i < mix.length; i++) {
                    int value = mix[i] + Math.round(frame[i] * gain);
                    mix[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
                }
            }

            output.clear();
            for (short sample : mix) {
                output.putShort(sample);
            }
            try {
                line.write(bytes, 0, bytes.length);
            } catch (Exception exception) {
                Boxier.LOGGER.warn("Browser audio output failed", exception);
                return;
            }
        }
    }

    private static void drainInbound() {
        Packet packet;
        while ((packet = INBOUND.poll()) != null) {
            Level level = Minecraft.getInstance().level;
            if (level == null) {
                continue;
            }
            Key key = new Key(level.dimension(), packet.pos());
            Channel channel = CHANNEL_MAP.get(key);
            if (channel == null) {
                try {
                    channel = new Channel(packet.pos());
                } catch (Exception exception) {
                    Boxier.LOGGER.warn("Failed to create browser audio channel", exception);
                    continue;
                }
                CHANNEL_MAP.put(key, channel);
            }
            short[] pcm = channel.decoder.decode(packet.data());
            if (pcm != null && pcm.length > 0) {
                channel.add(pcm);
            }
        }
    }

    private static float gain(Level level, BlockPos pos) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !player.level().dimension().equals(level.dimension())) {
            return 0.0F;
        }
        double dx = player.getX() - (pos.getX() + 0.5);
        double dy = player.getY() - (pos.getY() + 0.5);
        double dz = player.getZ() - (pos.getZ() + 0.5);
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float linear = (float) Math.max(0.0, Math.min(1.0, 1.0 - distance / FADE_DISTANCE));
        float distanceGain = linear * linear * (3.0F - 2.0F * linear);
        return distanceGain * volume;
    }

    private static void clearChannels() {
        for (Channel channel : CHANNEL_MAP.values()) {
            channel.close();
        }
        CHANNEL_MAP.clear();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private record Key(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private record Packet(BlockPos pos, byte[] data) {
    }

    private static final class Channel {
        private final BlockPos pos;
        private final BrowserOpusDecoder decoder;
        private final Deque<short[]> frames = new ArrayDeque<>();

        private Channel(BlockPos pos) {
            this.pos = pos;
            this.decoder = new BrowserOpusDecoder(CHANNELS);
        }

        private short[] pollFrame() {
            if (this.frames.size() < TARGET_FRAMES) {
                return null;
            }
            return this.frames.pollFirst();
        }

        private void add(short[] frame) {
            this.frames.addLast(frame);
            while (this.frames.size() > MAX_FRAMES) {
                this.frames.pollFirst();
            }
        }

        private void close() {
            this.decoder.close();
        }
    }
}
