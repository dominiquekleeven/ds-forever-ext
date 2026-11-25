package com.winlator.renderer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Issue 153: what the native renderer was last given for frame generation, so a settings push that changes nothing never
 * reaches it. Every push of the container's settings sends all four values again, and a shader push makes the renderer wait
 * for the GPU and rebuild its shaders even when the cache is the same; meanwhile window updates wait on the renderer's lock.
 * {@link #forget()} when a new native renderer is created or the old one is destroyed: it starts from its own defaults.
 * Not thread-safe: the renderer calls it holding its frame-generation lock.
 */
final class FrameGenPushes {
    // Oversized caches still reload; the bound limits work on an infrequent settings push, never a frame tick.
    static final long MAX_SHADER_CACHE_BYTES = 64L * 1024 * 1024;
    private boolean shadersSent, modeSent, refreshSent, enabledSent;
    private String shaders;
    private byte[] shaderDigest;
    private int multiplier, targetRate, flowScalePct;
    private float refreshHz;
    private boolean enabled;

    void forget() {
        shadersSent = modeSent = refreshSent = enabledSent = false;
        shaders = null;
        shaderDigest = null;
    }

    /** Ext: the cache writer reuses filenames after DLL changes, so path/size/mtime alone cannot identify its content. */
    boolean shaders(String cachePath) {
        byte[] digest = shaderDigest(cachePath);
        if (digest != null && shadersSent && Objects.equals(cachePath, shaders) && Arrays.equals(digest, shaderDigest)) return false;
        // A failed read must not suppress this push or a later retry, even if the old bytes return at the same path.
        shadersSent = digest != null;
        shaders = cachePath;
        shaderDigest = digest;
        return true;
    }

    private static byte[] shaderDigest(String cachePath) {
        if (cachePath == null) return null;
        try {
            File file = new File(cachePath);
            if (!file.isFile() || file.length() > MAX_SHADER_CACHE_BYTES) return null;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                long total = 0;
                int n;
                while ((n = input.read(buffer)) != -1) {
                    total += n;
                    if (total > MAX_SHADER_CACHE_BYTES) return null;
                    digest.update(buffer, 0, n);
                }
            }
            return digest.digest();
        } catch (IOException | SecurityException | NoSuchAlgorithmException e) {
            return null;
        }
    }

    boolean mode(int multiplier, int targetRate, int flowScalePct) {
        if (modeSent && multiplier == this.multiplier && targetRate == this.targetRate && flowScalePct == this.flowScalePct) return false;
        modeSent = true;
        this.multiplier = multiplier;
        this.targetRate = targetRate;
        this.flowScalePct = flowScalePct;
        return true;
    }

    boolean refreshRate(float hz) {
        if (refreshSent && Float.compare(hz, refreshHz) == 0) return false;
        refreshSent = true;
        refreshHz = hz;
        return true;
    }

    boolean enabled(boolean enabled) {
        if (enabledSent && enabled == this.enabled) return false;
        enabledSent = true;
        this.enabled = enabled;
        return true;
    }
}
