package com.lima.boxier;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class BrowserTextureCache {
    private static final ExecutorService DECODER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Boxier-Browser-Texture");
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<Key, Entry> ENTRIES = new ConcurrentHashMap<>();

    private BrowserTextureCache() {
    }

    public static Entry get(Level level, BlockPos pos) {
        return ENTRIES.get(new Key(level.dimension(), pos.immutable()));
    }

    public static void update(Level level, BlockPos pos, byte[] jpeg) {
        Key key = new Key(level.dimension(), pos.immutable());
        Entry entry = ENTRIES.computeIfAbsent(key, ignored -> new Entry());
        if (!entry.decoding.compareAndSet(false, true)) {
            return;
        }

        DECODER.execute(() -> {
            NativeImage image = null;
            try {
                image = decodeJpeg(jpeg);
                NativeImage decoded = image;
                Minecraft.getInstance().execute(() -> upload(key, entry, decoded));
            } catch (Exception exception) {
                if (image != null) {
                    image.close();
                }
                entry.decoding.set(false);
                Boxier.LOGGER.warn("Failed to decode browser frame", exception);
            }
        });
    }

    private static NativeImage decodeJpeg(byte[] data) throws Exception {
        BufferedImage buffered = ImageIO.read(new ByteArrayInputStream(data));
        if (buffered == null) {
            throw new IllegalStateException("Unsupported browser frame format");
        }
        int width = buffered.getWidth();
        int height = buffered.getHeight();
        int[] pixels = buffered.getRGB(0, 0, width, height, null, 0, width);
        NativeImage image = new NativeImage(width, height, false);
        ByteBuffer output = image.getPixelBytes();
        output.position(0);
        for (int pixel : pixels) {
            output.put((byte) (pixel >> 16));
            output.put((byte) (pixel >> 8));
            output.put((byte) pixel);
            output.put((byte) (pixel >>> 24));
        }
        return image;
    }

    private static void upload(Key key, Entry entry, NativeImage image) {
        try {
            int width = image.getWidth();
            int height = image.getHeight();
            if (entry.texture == null) {
                Identifier id = Identifier.fromNamespaceAndPath(Boxier.MODID, "browser/" + Integer.toHexString(key.hashCode()));
                DynamicTexture texture = new DynamicTexture(() -> "boxier:browser", image);
                Minecraft.getInstance().getTextureManager().register(id, texture);
                texture.upload();
                entry.texture = texture;
                entry.id = id;
            } else {
                entry.texture.setPixels(image);
                entry.texture.upload();
            }
            entry.width = width;
            entry.height = height;
        } catch (Exception exception) {
            image.close();
            Boxier.LOGGER.warn("Failed to upload browser frame", exception);
        } finally {
            entry.decoding.set(false);
        }
    }

    public static void remove(Level level, BlockPos pos) {
        Entry entry = ENTRIES.remove(new Key(level.dimension(), pos.immutable()));
        if (entry != null && entry.id != null) {
            Minecraft.getInstance().getTextureManager().release(entry.id);
        }
    }

    public static void clear() {
        for (Entry entry : ENTRIES.values()) {
            if (entry.id != null) {
                Minecraft.getInstance().getTextureManager().release(entry.id);
            }
        }
        ENTRIES.clear();
    }

    private record Key(ResourceKey<Level> dimension, BlockPos pos) {
    }

    public static final class Entry {
        private final AtomicBoolean decoding = new AtomicBoolean();
        private Identifier id;
        private DynamicTexture texture;
        private int width;
        private int height;

        public Identifier id() {
            return this.id;
        }

        public int width() {
            return this.width;
        }

        public int height() {
            return this.height;
        }
    }
}
