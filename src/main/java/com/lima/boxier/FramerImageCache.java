package com.lima.boxier;

import java.awt.AlphaComposite;
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
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class FramerImageCache {
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final int MAX_DIMENSION = 512;
    private static final int MAX_FRAMES = 128;

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private static final Map<String, Animation> ANIMATIONS = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();

    private FramerImageCache() {
    }

    public static Animation getAnimation(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        Animation cached = ANIMATIONS.get(url);
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
            Decoded decoded = null;
            try {
                byte[] bytes = fetch(url);
                if (bytes != null) {
                    // Decoding and compositing all frames is CPU heavy, so it runs off the render thread.
                    decoded = decodeFrames(bytes);
                }
            } catch (Exception exception) {
                Boxier.LOGGER.warn("Failed to decode framer image '{}'", url, exception);
            }

            Decoded result = decoded;
            Minecraft.getInstance().execute(() -> {
                try {
                    if (result != null) {
                        upload(url, result);
                    }
                } catch (Exception exception) {
                    Boxier.LOGGER.warn("Failed to upload framer image '{}'", url, exception);
                } finally {
                    PENDING.remove(url);
                }
            });
        });
    }

    private static byte[] fetch(String url) {
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return null;
            }

            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Boxier/Framer")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 == 2 && response.body().length <= MAX_BYTES) {
                return response.body();
            }
        } catch (Exception exception) {
            Boxier.LOGGER.warn("Failed to download framer image '{}'", url, exception);
        }
        return null;
    }

    private static Decoded decodeFrames(byte[] bytes) throws Exception {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (stream == null) {
                return null;
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return null;
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, false, false);
                int logicalWidth = reader.getWidth(0);
                int logicalHeight = reader.getHeight(0);
                int frameCount = Math.min(Math.max(1, readFrameCount(reader)), MAX_FRAMES);

                // Frames are raw deltas at a sub-rectangle, so they are composited onto a
                // shared canvas, honouring each frame's disposal method and transparency.
                BufferedImage canvas = new BufferedImage(logicalWidth, logicalHeight, BufferedImage.TYPE_INT_ARGB);
                BufferedImage[] frames = new BufferedImage[frameCount];
                int[] delays = new int[frameCount];

                int previousDisposal = 0;
                int previousX = 0;
                int previousY = 0;
                int previousWidth = logicalWidth;
                int previousHeight = logicalHeight;
                BufferedImage restoreSnapshot = null;

                for (int index = 0; index < frameCount; index++) {
                    if (index > 0) {
                        if (previousDisposal == 2) {
                            clearRect(canvas, previousX, previousY, previousWidth, previousHeight);
                        } else if (previousDisposal == 3 && restoreSnapshot != null) {
                            canvas = copyOf(restoreSnapshot);
                        }
                    }

                    BufferedImage frame = reader.read(index);
                    FrameInfo info = readFrameInfo(reader, index, logicalWidth, logicalHeight);

                    if (info.disposal == 3) {
                        restoreSnapshot = copyOf(canvas);
                    }

                    Graphics2D graphics = canvas.createGraphics();
                    graphics.setComposite(AlphaComposite.SrcOver);
                    graphics.drawImage(frame, info.x, info.y, null);
                    graphics.dispose();

                    frames[index] = downscale(copyOf(canvas));
                    delays[index] = info.delay;

                    previousDisposal = info.disposal;
                    previousX = info.x;
                    previousY = info.y;
                    previousWidth = info.width;
                    previousHeight = info.height;
                }

                return new Decoded(frames, delays);
            } finally {
                reader.dispose();
            }
        }
    }

    // Texture creation touches GL, so this runs on the render thread.
    private static void upload(String url, Decoded decoded) throws Exception {
        Identifier[] textures = new Identifier[decoded.frames.length];
        for (int index = 0; index < decoded.frames.length; index++) {
            textures[index] = uploadFrame(url.hashCode(), index, decoded.frames[index]);
        }
        ANIMATIONS.put(url, new Animation(textures, decoded.delays));
    }

    private static int readFrameCount(ImageReader reader) {
        try {
            return reader.getNumImages(true);
        } catch (Exception exception) {
            return 1;
        }
    }

    private static FrameInfo readFrameInfo(ImageReader reader, int index, int logicalWidth, int logicalHeight) {
        int x = 0;
        int y = 0;
        int width = logicalWidth;
        int height = logicalHeight;
        int disposal = 0;
        int delay = 100;

        try {
            IIOMetadata metadata = reader.getImageMetadata(index);
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metadata.getNativeMetadataFormatName());

            IIOMetadataNode descriptor = firstNode(root, "ImageDescriptor");
            if (descriptor != null) {
                x = parseInt(descriptor.getAttribute("imageLeftPosition"), 0);
                y = parseInt(descriptor.getAttribute("imageTopPosition"), 0);
                width = parseInt(descriptor.getAttribute("imageWidth"), logicalWidth);
                height = parseInt(descriptor.getAttribute("imageHeight"), logicalHeight);
            }

            IIOMetadataNode control = firstNode(root, "GraphicControlExtension");
            if (control != null) {
                disposal = disposalValue(control.getAttribute("disposalMethod"));
                delay = Math.max(1, parseInt(control.getAttribute("delayTime"), 10)) * 10;
            }
        } catch (Exception exception) {
            // Use the defaults.
        }

        return new FrameInfo(x, y, width, height, disposal, delay);
    }

    private static IIOMetadataNode firstNode(IIOMetadataNode root, String name) {
        return (IIOMetadataNode) root.getElementsByTagName(name).item(0);
    }

    private static int disposalValue(String value) {
        if (value.contains("Background")) {
            return 2;
        }
        if (value.contains("Previous")) {
            return 3;
        }
        return 0;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception exception) {
            return fallback;
        }
    }

    private static void clearRect(BufferedImage canvas, int x, int y, int width, int height) {
        Graphics2D graphics = canvas.createGraphics();
        graphics.setComposite(AlphaComposite.Clear);
        graphics.fillRect(x, y, width, height);
        graphics.dispose();
    }

    private static BufferedImage copyOf(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return copy;
    }

    private static Identifier uploadFrame(int urlHash, int index, BufferedImage frame) throws Exception {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        if (!ImageIO.write(frame, "PNG", png)) {
            throw new IllegalStateException("No PNG writer available");
        }

        NativeImage image = NativeImage.read(png.toByteArray());
        Identifier id = Identifier.fromNamespaceAndPath(Boxier.MODID, "framer/" + Integer.toHexString(urlHash) + "_" + index);
        DynamicTexture texture = new DynamicTexture(() -> "boxier:framer", image);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        texture.upload();
        return id;
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

    private record FrameInfo(int x, int y, int width, int height, int disposal, int delay) {
    }

    private record Decoded(BufferedImage[] frames, int[] delays) {
    }

    public static final class Animation {
        private final Identifier[] frames;
        private final long[] cumulative;
        private final long total;

        private Animation(Identifier[] frames, int[] delays) {
            this.frames = frames;
            this.cumulative = new long[frames.length];
            long sum = 0L;
            for (int index = 0; index < frames.length; index++) {
                sum += Math.max(1, delays[index]);
                this.cumulative[index] = sum;
            }
            this.total = sum;
        }

        public boolean isAnimated() {
            return this.frames.length > 1;
        }

        public Identifier frameAt(long millis) {
            if (this.frames.length == 1) {
                return this.frames[0];
            }
            long time = Math.floorMod(millis, this.total);
            for (int index = 0; index < this.cumulative.length; index++) {
                if (time < this.cumulative[index]) {
                    return this.frames[index];
                }
            }
            return this.frames[this.frames.length - 1];
        }
    }
}
