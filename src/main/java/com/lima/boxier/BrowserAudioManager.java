package com.lima.boxier;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class BrowserAudioManager {
    private static final Map<Key, Channel> CHANNELS = new HashMap<>();

    private BrowserAudioManager() {
    }

    public static void play(Level level, BlockPos pos, byte[] packet) {
        Key key = new Key(level.dimension(), pos.immutable());
        Channel channel = CHANNELS.computeIfAbsent(key, ignored -> new Channel(pos));
        channel.play(packet);
    }

    public static void clear() {
        for (Channel channel : CHANNELS.values()) {
            channel.close();
        }
        CHANNELS.clear();
    }

    private record Key(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private static final class Channel {
        private static final int BUFFER_COUNT = 24;
        private static final int MIN_BUFFERS = 6;

        private final BlockPos pos;
        private final Deque<Integer> freeBuffers = new ArrayDeque<>();
        private BrowserOpusDecoder decoder;
        private int source;
        private int[] buffers;
        private boolean loggedPacket;
        private long lastPacketAt;

        private Channel(BlockPos pos) {
            this.pos = pos;
        }

        private void play(byte[] packet) {
            long now = System.currentTimeMillis();
            if (this.source != 0) {
                int state = AL10.alGetSourcei(this.source, AL10.AL_SOURCE_STATE);
                boolean silentLongEnough = this.lastPacketAt > 0 && now - this.lastPacketAt > 500;
                if (state != AL10.AL_PLAYING && (silentLongEnough || this.freeBuffers.isEmpty())) {
                    resetSource();
                }
            }
            this.lastPacketAt = now;
            if (!ensureOpen()) {
                return;
            }
            short[] samples = this.decoder.decode(packet);
            if (samples == null || samples.length == 0) {
                return;
            }
            if (!this.loggedPacket) {
                this.loggedPacket = true;
                Boxier.LOGGER.info("Browser audio packet received at {} samples={}", this.pos, samples.length);
            }

            reclaim();
            Integer buffer = this.freeBuffers.poll();
            if (buffer == null) {
                return;
            }

            ByteBuffer pcm = BufferUtils.createByteBuffer(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (short sample : samples) {
                pcm.putShort(sample);
            }
            pcm.flip();

            updateGain();
            AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, pcm, BrowserOpusEncoder.SAMPLE_RATE);
            AL10.alSourceQueueBuffers(this.source, buffer);
            int queued = BUFFER_COUNT - this.freeBuffers.size();
            if (AL10.alGetSourcei(this.source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING
                    && queued >= MIN_BUFFERS) {
                AL10.alSourcePlay(this.source);
            }
        }

        private void updateGain() {
            Player player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            double dx = player.getX() - (this.pos.getX() + 0.5);
            double dy = player.getY() - (this.pos.getY() + 0.5);
            double dz = player.getZ() - (this.pos.getZ() + 0.5);
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            float linear = (float) Math.max(0.0, Math.min(1.0, 1.0 - distance / 32.0));
            float gain = linear * linear * (3.0F - 2.0F * linear);
            AL10.alSourcef(this.source, AL10.AL_GAIN, gain);
        }

        private void resetSource() {
            if (this.source != 0) {
                AL10.alSourceStop(this.source);
                AL10.alDeleteSources(this.source);
                this.source = 0;
            }
            if (this.buffers != null) {
                AL10.alDeleteBuffers(this.buffers);
                this.buffers = null;
            }
            this.freeBuffers.clear();
            Boxier.LOGGER.info("Reset browser audio channel at {}", this.pos);
        }

        private void reclaim() {
            int processed = AL10.alGetSourcei(this.source, AL10.AL_BUFFERS_PROCESSED);
            while (processed-- > 0) {
                int buffer = AL10.alSourceUnqueueBuffers(this.source);
                if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                    break;
                }
                this.freeBuffers.add(buffer);
            }
        }

        private boolean ensureOpen() {
            if (this.source != 0) {
                return true;
            }
            if (!BrowserOpus.isAvailable()) {
                return false;
            }
            try {
                this.decoder = new BrowserOpusDecoder();
                this.source = AL10.alGenSources();
                this.buffers = new int[BUFFER_COUNT];
                AL10.alGenBuffers(this.buffers);
                for (int buffer : this.buffers) {
                    this.freeBuffers.add(buffer);
                }
                AL10.alSourcef(this.source, AL10.AL_ROLLOFF_FACTOR, 0.0F);
                AL10.alSourcef(this.source, AL10.AL_REFERENCE_DISTANCE, 1.0F);
                AL10.alSourcef(this.source, AL10.AL_MAX_DISTANCE, 64.0F);
                AL10.alSourcei(this.source, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
                AL10.alSource3f(this.source, AL10.AL_POSITION, this.pos.getX() + 0.5F, this.pos.getY() + 0.5F,
                        this.pos.getZ() + 0.5F);
                Boxier.LOGGER.info("Browser audio channel created at {}", this.pos);
                return true;
            } catch (Exception exception) {
                Boxier.LOGGER.warn("Failed to create browser audio channel", exception);
                close();
                return false;
            }
        }

        private void close() {
            if (this.source != 0) {
                AL10.alSourceStop(this.source);
                AL10.alDeleteSources(this.source);
                this.source = 0;
            }
            if (this.buffers != null) {
                AL10.alDeleteBuffers(this.buffers);
                this.buffers = null;
            }
            if (this.decoder != null) {
                this.decoder.close();
                this.decoder = null;
            }
        }
    }
}
