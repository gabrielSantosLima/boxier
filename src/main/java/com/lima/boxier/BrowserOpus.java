package com.lima.boxier;

import club.minnced.opus.util.OpusLibrary;

public final class BrowserOpus {
    private static final boolean AVAILABLE;

    static {
        boolean available = false;
        try {
            OpusLibrary.loadFromJar();
            available = true;
        } catch (Throwable throwable) {
            Boxier.LOGGER.warn("Failed to load the Opus native library", throwable);
        }
        AVAILABLE = available;
    }

    private BrowserOpus() {
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }
}
