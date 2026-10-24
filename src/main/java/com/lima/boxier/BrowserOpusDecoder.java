package com.lima.boxier;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import com.sun.jna.ptr.PointerByReference;

import tomp2p.opuswrapper.Opus;

public final class BrowserOpusDecoder implements AutoCloseable {
    private static final int MAX_FRAME_SAMPLES = 5760;

    private final PointerByReference decoder;
    private final ShortBuffer output = ByteBuffer.allocateDirect(MAX_FRAME_SAMPLES * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer();

    public BrowserOpusDecoder() {
        if (!BrowserOpus.isAvailable()) {
            throw new IllegalStateException("Opus native library is unavailable");
        }
        IntBuffer error = IntBuffer.allocate(1);
        this.decoder = Opus.INSTANCE.opus_decoder_create(BrowserOpusEncoder.SAMPLE_RATE, 1, error);
        if (error.get(0) != Opus.OPUS_OK) {
            throw new IllegalStateException("Failed to create Opus decoder: " + error.get(0));
        }
    }

    public short[] decode(byte[] packet) {
        this.output.clear();
        int samples = Opus.INSTANCE.opus_decode(this.decoder, packet, packet.length, this.output, MAX_FRAME_SAMPLES, 0);
        if (samples <= 0) {
            return null;
        }
        short[] result = new short[samples];
        this.output.position(0);
        this.output.get(result);
        return result;
    }

    @Override
    public void close() {
        Opus.INSTANCE.opus_decoder_destroy(this.decoder);
    }
}
