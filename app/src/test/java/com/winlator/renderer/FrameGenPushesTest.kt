package com.winlator.renderer

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Issue 153: an unchanged push never reaches the native renderer (no GPU wait, no rebuild). */
class FrameGenPushesTest {
    @get:Rule val temp = TemporaryFolder()
    private fun cache(name: String = "cache") = temp.newFile(name).apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }

    @Test fun theSameShadersAreSentOnce() {
        val p = FrameGenPushes()
        val a = cache("a")
        val b = cache("b")
        assertTrue(p.shaders(a.path))
        assertFalse(p.shaders(a.path))
        a.writeBytes(a.readBytes())
        assertFalse("rewritten identical bytes still skip", p.shaders(a.path))
        assertTrue("distinct path reloads even with identical bytes", p.shaders(b.path))
        assertFalse(p.shaders(b.path))
    }

    @Test fun changedContentAtTheSamePathReloadsWithTheSameLengthAndMtime() {
        val p = FrameGenPushes()
        val a = cache()
        val time = FileTime.fromMillis(1_700_000_000_000L)
        Files.setLastModifiedTime(a.toPath(), time)
        assertTrue(p.shaders(a.path))
        a.writeBytes(byteArrayOf(4, 3, 2, 1))
        Files.setLastModifiedTime(a.toPath(), time)
        assertTrue("same path, length and mtime but new bytes must reload", p.shaders(a.path))
        assertFalse(p.shaders(a.path))
    }

    @Test fun missingOrInvalidCacheAlwaysRetriesAndForgetsOldContent() {
        val p = FrameGenPushes()
        val a = cache()
        assertTrue(p.shaders(a.path))
        assertTrue(a.delete())
        assertTrue(p.shaders(a.path))
        assertTrue(p.shaders(a.path))
        a.writeBytes(byteArrayOf(1, 2, 3, 4))
        assertTrue("restored old bytes must retry after a failed read", p.shaders(a.path))
        assertFalse(p.shaders(a.path))
        val directory = temp.newFolder("directory")
        assertTrue(p.shaders(directory.path))
        assertTrue(p.shaders(directory.path))
        assertTrue(p.shaders(null))
        assertTrue(p.shaders(null))
    }

    @Test fun unreadableCacheAlwaysRetries() {
        val p = FrameGenPushes()
        val a = cache()
        assertTrue(p.shaders(a.path))
        try {
            assertTrue(a.setReadable(false, false))
            assumeFalse("a privileged test process can still read this file", a.canRead())
            assertTrue(p.shaders(a.path))
            assertTrue(p.shaders(a.path))
        } finally {
            a.setReadable(true, true)
        }
    }

    @Test fun oversizedCacheAlwaysRetriesWithoutReadingUnboundedContent() {
        val p = FrameGenPushes()
        val a = cache()
        RandomAccessFile(a, "rw").use { it.setLength(FrameGenPushes.MAX_SHADER_CACHE_BYTES + 1) }
        assertTrue(p.shaders(a.path))
        assertTrue(p.shaders(a.path))
    }

    @Test fun theSameModeRefreshAndSwitchAreSentOnce() {
        val p = FrameGenPushes()
        assertTrue(p.mode(2, 0, 70)); assertFalse(p.mode(2, 0, 70))
        assertTrue(p.mode(3, 0, 70)); assertTrue(p.mode(3, 0, 50)); assertTrue(p.mode(3, 60, 50)); assertFalse(p.mode(3, 60, 50))
        assertTrue(p.refreshRate(60f)); assertFalse(p.refreshRate(60f)); assertTrue(p.refreshRate(120f))
        assertTrue(p.enabled(false)); assertFalse(p.enabled(false)); assertTrue(p.enabled(true)); assertFalse(p.enabled(true))
    }

    @Test fun theFirstPushAlwaysGoesEvenAtTheDefaults() {
        val p = FrameGenPushes()
        assertTrue(p.enabled(false))
        assertTrue(p.refreshRate(0f))
        assertTrue(p.mode(0, 0, 0))
    }

    @Test fun aNewRendererIsSentEverythingAgain() {
        val p = FrameGenPushes()
        val a = cache()
        p.shaders(a.path); p.mode(2, 0, 70); p.refreshRate(60f); p.enabled(true)
        p.forget()
        assertTrue(p.shaders(a.path))
        assertTrue(p.mode(2, 0, 70))
        assertTrue(p.refreshRate(60f))
        assertTrue(p.enabled(true))
        assertFalse(p.shaders(a.path))
    }

    @Test fun aChangedValueThenTheOldOneIsSentBothTimes() {
        val p = FrameGenPushes()
        val a = cache("a"); val b = cache("b")
        assertTrue(p.shaders(a.path)); assertTrue(p.shaders(b.path)); assertTrue(p.shaders(a.path))
        assertTrue(p.enabled(true)); assertTrue(p.enabled(false)); assertTrue(p.enabled(true))
    }
}
