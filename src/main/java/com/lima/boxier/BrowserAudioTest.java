package com.lima.boxier;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BrowserAudioTest {
    private BrowserAudioTest() {
    }

    public static void sendTone(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        CompletableFuture.runAsync(() -> {
            int frames = 100;
            short[] pcm = new short[BrowserOpusEncoder.FRAME_SIZE * 2];
            try (BrowserOpusEncoder encoder = new BrowserOpusEncoder(2)) {
                for (int frame = 0; frame < frames; frame++) {
                    for (int i = 0; i < BrowserOpusEncoder.FRAME_SIZE; i++) {
                        short sample = (short) (Math.sin(2 * Math.PI * 440
                                * (frame * BrowserOpusEncoder.FRAME_SIZE + i) / 48000.0) * 12000);
                        pcm[i * 2] = sample;
                        pcm[i * 2 + 1] = sample;
                    }
                    List<byte[]> packets = encoder.encode(pcm, 48000);
                    for (byte[] packet : packets) {
                        PacketDistributor.sendToPlayer(player, new BrowserAudioPayload(pos, packet));
                    }
                    Thread.sleep(20);
                }
            } catch (Exception exception) {
                Boxier.LOGGER.warn("Browser audio test failed", exception);
            }
        });
    }
}
