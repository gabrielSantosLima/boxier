package com.lima.boxier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class BrowserClientState {
    private static final long CURSOR_TIMEOUT_MS = 2000L;
    private static final Map<Key, Cursor> CURSORS = new ConcurrentHashMap<>();

    private BrowserClientState() {
    }

    public static void setCursor(Level level, BlockPos pos, float x, float y) {
        CURSORS.put(new Key(level.dimension(), pos.immutable()), new Cursor(x, y, System.currentTimeMillis()));
    }

    public static Cursor getCursor(Level level, BlockPos pos) {
        Cursor cursor = CURSORS.get(new Key(level.dimension(), pos.immutable()));
        if (cursor == null || System.currentTimeMillis() - cursor.updatedAt() > CURSOR_TIMEOUT_MS) {
            return null;
        }
        return cursor;
    }

    public static void clear() {
        CURSORS.clear();
    }

    private record Key(ResourceKey<Level> dimension, BlockPos pos) {
    }

    public record Cursor(float x, float y, long updatedAt) {
    }
}
