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
    private static final int BITRATE = 256000;

    private final int channels;
    private final PointerByReference encoder;
    private final ByteBuffer output = ByteBuffer.allocateDirect(8000);
    private short[] pending;
    private int pendingCount;

    public BrowserOpusEncoder() {
        this(2);
    }

    public BrowserOpusEncoder(int channels) {
        if (channels != 1 && channels != 2) {
            throw new IllegalArgumentException("Unsupported channel count: " + channels);
        }
        this.channels = channels;
        this.pending = new short[FRAME_SIZE * channels * 4];
        if (!BrowserOpus.isAvailable()) {
            throw new IllegalStateException("Opus native library is unavailable");
        }
        IntBuffer error = IntBuffer.allocate(1);
        this.encoder = Opus.INSTANCE.opus_encoder_create(SAMPLE_RATE, channels, Opus.OPUS_APPLICATION_AUDIO, error);
        if (error.get(0) != Opus.OPUS_OK) {
            throw new IllegalStateException("Failed to create Opus encoder: " + error.get(0));
        }
        Opus.INSTANCE.opus_encoder_ctl(this.encoder, Opus.OPUS_SET_BITRATE_REQUEST, BITRATE);
        Opus.INSTANCE.opus_encoder_ctl(this.encoder, Opus.OPUS_SET_COMPLEXITY_REQUEST, 10);
        Opus.INSTANCE.opus_encoder_ctl(this.encoder, Opus.OPUS_SET_VBR_REQUEST, 1);
        Opus.INSTANCE.opus_encoder_ctl(this.encoder, Opus.OPUS_SET_BANDWIDTH_REQUEST, Opus.OPUS_BANDWIDTH_FULLBAND);
        Opus.INSTANCE.opus_encoder_ctl(this.encoder, Opus.OPUS_SET_SIGNAL_REQUEST, Opus.OPUS_SIGNAL_MUSIC);
        Opus.INSTANCE.opus_encoder_ctl(this.encoder, Opus.OPUS_SET_INBAND_FEC_REQUEST, 1);
        Opus.INSTANCE.opus_encoder_ctl(this.encoder, Opus.OPUS_SET_PACKET_LOSS_PERC_REQUEST, 10);
    }

    public int getChannels() {
        return this.channels;
    }

    public List<byte[]> encode(short[] samples, int sourceRate) {
        short[] resampled = sourceRate == SAMPLE_RATE ? samples : resample(samples, sourceRate);
        append(resampled);

        int frameSamples = FRAME_SIZE * this.channels;
        List<byte[]> frames = new ArrayList<>();
        while (this.pendingCount >= frameSamples) {
            byte[] frame = encodeFrame(frameSamples);
            if (frame != null) {
                frames.add(frame);
            }
            System.arraycopy(this.pending, frameSamples, this.pending, 0, this.pendingCount - frameSamples);
            this.pendingCount -= frameSamples;
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

    private byte[] encodeFrame(int frameSamples) {
        this.output.clear();
        ShortBuffer input = ShortBuffer.wrap(this.pending, 0, frameSamples);
        int length = Opus.INSTANCE.opus_encode(this.encoder, input, FRAME_SIZE, this.output, this.output.capacity());
        if (length <= 0) {
            return null;
        }
        byte[] frame = new byte[length];
        this.output.position(0);
        this.output.get(frame);
        return frame;
    }

    private short[] resample(short[] input, int sourceRate) {
        if (sourceRate <= 0 || input.length == 0) {
            return input;
        }
        int inputFrames = input.length / this.channels;
        int outputFrames = Math.max(1, (int) Math.round(inputFrames * (double) SAMPLE_RATE / sourceRate));
        short[] output = new short[outputFrames * this.channels];
        for (int frame = 0; frame < outputFrames; frame++) {
            double source = frame * (double) sourceRate / SAMPLE_RATE;
            int left = Math.min((int) source, inputFrames - 1);
            int right = Math.min(left + 1, inputFrames - 1);
            double fraction = source - Math.floor(source);
            for (int channel = 0; channel < this.channels; channel++) {
                int leftIndex = left * this.channels + channel;
                int rightIndex = right * this.channels + channel;
                output[frame * this.channels + channel] = (short) Math.round(
                        input[leftIndex] + (input[rightIndex] - input[leftIndex]) * fraction);
            }
        }
        return output;
    }

    @Override
    public void close() {
        Opus.INSTANCE.opus_encoder_destroy(this.encoder);
    }
}
