package com.lima.boxier;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class FramerImageCache {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_DIMENSION = 512;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private static final Map<String, Identifier> TEXTURES = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();

    private FramerImageCache() {
    }

    public static Identifier getTexture(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        Identifier cached = TEXTURES.get(url);
        if (cached != null) {
            return cached;
        }

        if (PENDING.add(url)) {
            download(url);
        }
        return null;
    }

    private static void download(String url) {
        CompletableFuture.runAsync(() -> {
            byte[] bytes = null;
            try {
                URI uri = URI.create(url.trim());
                String scheme = uri.getScheme();
                if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                    return;
                }

                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(20))
                        .header("User-Agent", "Boxier/Framer")
                        .GET()
                        .build();
                HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() / 100 == 2 && response.body().length <= MAX_BYTES) {
                    bytes = response.body();
                }
            } catch (Exception exception) {
                Boxier.LOGGER.warn("Failed to download framer image '{}'", url, exception);
            }

            byte[] imageBytes = bytes;
            Minecraft.getInstance().execute(() -> {
                try {
                    if (imageBytes != null) {
                        register(url, imageBytes);
                    }
                } finally {
                    PENDING.remove(url);
                }
            });
        });
    }

    private static void register(String url, byte[] bytes) {
        try {
            NativeImage image = decode(bytes);
            if (image == null) {
                Boxier.LOGGER.warn("Unsupported framer image '{}'", url);
                return;
            }

            Identifier id = Identifier.fromNamespaceAndPath(Boxier.MODID, "framer/" + Integer.toHexString(url.hashCode()));
            DynamicTexture texture = new DynamicTexture(() -> "boxier:framer", image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            texture.upload();
            TEXTURES.put(url, id);
        } catch (Exception exception) {
            Boxier.LOGGER.warn("Failed to decode framer image '{}'", url, exception);
        }
    }

    private static NativeImage decode(byte[] bytes) throws Exception {
        BufferedImage buffered = ImageIO.read(new ByteArrayInputStream(bytes));
        if (buffered == null) {
            return null;
        }

        buffered = downscale(buffered);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        if (!ImageIO.write(buffered, "PNG", png)) {
            return null;
        }
        return NativeImage.read(png.toByteArray());
    }

    private static BufferedImage downscale(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= MAX_DIMENSION && height <= MAX_DIMENSION) {
            return source;
        }

        double scale = Math.min((double) MAX_DIMENSION / width, (double) MAX_DIMENSION / height);
        int scaledWidth = Math.max(1, (int) Math.round(width * scale));
        int scaledHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage scaled = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, scaledWidth, scaledHeight, null);
        graphics.dispose();
        return scaled;
    }
}
