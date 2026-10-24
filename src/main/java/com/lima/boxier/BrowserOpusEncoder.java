package com.lima.boxier;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

import com.sun.jna.ptr.PointerByReference;

import tomp2p.opuswrapper.Opus;

public final class BrowserOpusEncoder implements AutoCloseable {
    public static final int SAMPLE_RATE = 48000;
    public static final int FRAME_SIZE = 960;

    private final PointerByReference encoder;
    private final ByteBuffer output = ByteBuffer.allocateDirect(4000);
    private short[] pending = new short[FRAME_SIZE * 4];
    private int pendingCount;

    public BrowserOpusEncoder() {
        if (!BrowserOpus.isAvailable()) {
            throw new IllegalStateException("Opus native library is unavailable");
        }
        IntBuffer error = IntBuffer.allocate(1);
        this.encoder = Opus.INSTANCE.opus_encoder_create(SAMPLE_RATE, 1, Opus.OPUS_APPLICATION_AUDIO, error);
        if (error.get(0) != Opus.OPUS_OK) {
            throw new IllegalStateException("Failed to create Opus encoder: " + error.get(0));
        }
        Opus.INSTANCE.opus_encoder_ctl(this.encoder, Opus.OPUS_SET_BITRATE_REQUEST, 64000);
        Opus.INSTANCE.opus_encoder_ctl(this.encoder, Opus.OPUS_SET_COMPLEXITY_REQUEST, 5);
        Opus.INSTANCE.opus_encoder_ctl(this.encoder, Opus.OPUS_SET_SIGNAL_REQUEST, Opus.OPUS_SIGNAL_MUSIC);
    }

    public List<byte[]> encode(short[] samples, int sourceRate) {
        short[] resampled = sourceRate == SAMPLE_RATE ? samples : resample(samples, sourceRate);
        append(resampled);

        List<byte[]> frames = new ArrayList<>();
        while (this.pendingCount >= FRAME_SIZE) {
            byte[] frame = encodeFrame();
            if (frame != null) {
                frames.add(frame);
            }
            System.arraycopy(this.pending, FRAME_SIZE, this.pending, 0, this.pendingCount - FRAME_SIZE);
            this.pendingCount -= FRAME_SIZE;
        }
        return frames;
    }

    private void append(short[] samples) {
        if (this.pendingCount + samples.length > this.pending.length) {
            int size = this.pending.length;
            while (size < this.pendingCount + samples.length) {
                size *= 2;
            }
            short[] grown = new short[size];
            System.arraycopy(this.pending, 0, grown, 0, this.pendingCount);
            this.pending = grown;
        }
        System.arraycopy(samples, 0, this.pending, this.pendingCount, samples.length);
        this.pendingCount += samples.length;
    }

    private byte[] encodeFrame() {
        this.output.clear();
        ShortBuffer input = ShortBuffer.wrap(this.pending, 0, FRAME_SIZE);
        int length = Opus.INSTANCE.opus_encode(this.encoder, input, FRAME_SIZE, this.output, this.output.capacity());
        if (length <= 0) {
            return null;
        }
        byte[] frame = new byte[length];
        this.output.position(0);
        this.output.get(frame);
        return frame;
    }

    private static short[] resample(short[] input, int sourceRate) {
        if (sourceRate <= 0 || input.length == 0) {
            return input;
        }
        double ratio = (double) SAMPLE_RATE / sourceRate;
        int outputLength = Math.max(1, (int) Math.floor(input.length * ratio));
        short[] output = new short[outputLength];
        for (int index = 0; index < outputLength; index++) {
            double source = index / ratio;
            int left = Math.min((int) source, input.length - 1);
            int right = Math.min(left + 1, input.length - 1);
            double fraction = source - Math.floor(source);
            output[index] = (short) Math.round(input[left] + (input[right] - input[left]) * fraction);
        }
        return output;
    }

    @Override
    public void close() {
        Opus.INSTANCE.opus_encoder_destroy(this.encoder);
    }
}
