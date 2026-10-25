package com.lima.boxier;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import com.sun.jna.ptr.PointerByReference;

import tomp2p.opuswrapper.Opus;

public final class BrowserOpusDecoder implements AutoCloseable {
    private static final int MAX_FRAME_SAMPLES = 5760;

    private final int channels;
    private final PointerByReference decoder;
    private final ShortBuffer output;

    public BrowserOpusDecoder() {
        this(2);
    }

    public BrowserOpusDecoder(int channels) {
        if (channels != 1 && channels != 2) {
            throw new IllegalArgumentException("Unsupported channel count: " + channels);
        }
        this.channels = channels;
        if (!BrowserOpus.isAvailable()) {
            throw new IllegalStateException("Opus native library is unavailable");
        }
        this.output = ByteBuffer.allocateDirect(MAX_FRAME_SAMPLES * channels * 2)
                .order(ByteOrder.nativeOrder()).asShortBuffer();
        IntBuffer error = IntBuffer.allocate(1);
        this.decoder = Opus.INSTANCE.opus_decoder_create(BrowserOpusEncoder.SAMPLE_RATE, channels, error);
        if (error.get(0) != Opus.OPUS_OK) {
            throw new IllegalStateException("Failed to create Opus decoder: " + error.get(0));
        }
    }

    public int getChannels() {
        return this.channels;
    }

    public short[] decode(byte[] packet) {
        this.output.clear();
        int samples = Opus.INSTANCE.opus_decode(this.decoder, packet, packet.length, this.output,
                MAX_FRAME_SAMPLES, 0);
        if (samples <= 0) {
            return null;
        }
        short[] result = new short[samples * this.channels];
        this.output.position(0);
        this.output.get(result);
        return result;
    }

    @Override
    public void close() {
        Opus.INSTANCE.opus_decoder_destroy(this.decoder);
    }
}
