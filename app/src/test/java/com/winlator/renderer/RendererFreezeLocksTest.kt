package com.winlator.renderer

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Java-only issue 153 mitigation for the existing prebuilt renderer. This does not test or repair native locks. */
class RendererFreezeLocksTest {
    private val app = listOf(File("."), File("app")).first { File(it, "src/main/java/com/winlator/renderer").isDirectory }
    private val renderer get() = File(app, "src/main/java/com/winlator/renderer/VulkanRenderer.java").readText()

    private fun close(t: String, open: Int): Int {
        var depth = 0; var k = open
        while (k < t.length) { if (t[k] == '{') depth++ else if (t[k] == '}') { depth--; if (depth == 0) return k }; k++ }
        error("unclosed block")
    }
    private fun body(t: String, head: String): String {
        val i = t.indexOf(head); require(i >= 0) { head }
        val start = t.indexOf('{', i)
        return t.substring(start, close(t, start) + 1)
    }
    private fun blocks(t: String, head: String): List<IntRange> {
        val out = ArrayList<IntRange>(); var i = t.indexOf(head)
        while (i >= 0) { val open = t.indexOf('{', i); out += open..close(t, open); i = t.indexOf(head, i + 1) }
        return out
    }
    private fun all(t: String, s: String): List<Int> {
        val out = ArrayList<Int>(); var i = t.indexOf(s)
        while (i >= 0) { out += i; i = t.indexOf(s, i + 1) }
        return out
    }

    @Test fun frameGenerationPushesNeverHoldTheSceneLock() {
        val r = renderer
        val sceneLock = blocks(r, "synchronized (lock)")
        assertTrue(sceneLock.size > 10)
        for (head in listOf("public void setFrameGenerationEnabled(boolean enabled)",
                            "public void setFrameGenerationShaders(String cachePath)",
                            "public void setFrameGenerationMode(int multiplier, int targetRate, int flowScalePct)",
                            "public void setFrameGenerationRefreshRate(float hz)")) {
            assertTrue(head, body(r, head).removePrefix("{").trim().startsWith("synchronized (frameGenLock)"))
        }
        val pushes = Regex("""nativeSetFrameGeneration\w+\((handle|nativeHandle)\b""").findAll(r).map { it.range.first }.toList()
        assertTrue(pushes.size >= 8)
        for (at in pushes) assertFalse("line ${r.substring(0, at).count { it == '\n' } + 1}", sceneLock.any { at in it })
        assertFalse(r.contains("applyFrameGenerationSettingsLocked"))
        val applies = all(r, "applyFrameGenerationSettings();")
        assertTrue(applies.size >= 2)
        for (at in applies) assertFalse(sceneLock.any { at in it })
    }

    @Test fun pushesThatChangeNothingNeverReachTheNativeRenderer() {
        val r = renderer
        val pushes = Regex("""nativeSetFrameGeneration\w+\((handle|nativeHandle)\b""").findAll(r).map { it.range.first }.toList()
        for (at in pushes) {
            val line = r.substring(r.lastIndexOf('\n', r.lastIndexOf('\n', at) - 1), at)
            assertTrue(line, line.contains("frameGenPushes."))
        }
        val init = r.indexOf("nativeHandle = CursorCanvas.initPrimed(")
        assertTrue(r.lastIndexOf("frameGenPushes.forget();", init) > r.lastIndexOf("synchronized (lock)", init))
        for (at in all(r, "nativeDestroy(nativeHandle);")) {
            assertTrue(r.indexOf("frameGenPushes.forget();", at) in at until r.indexOf('}', at))
        }
    }

    @Test fun noRenderListGoesInWhileAShaderOrSwitchPushRuns() {
        val r = renderer
        val slow = Regex("""nativeSetFrameGeneration(Shaders|Enabled)\((handle|nativeHandle)\b""").findAll(r).map { it.range.first }.toList()
        assertTrue(slow.size >= 4)
        for (at in slow) assertTrue(r.substring(r.lastIndexOf('\n', at), at).contains("slowFrameGenPush(() -> "))
        val push = body(r, "private void slowFrameGenPush(Runnable push)")
        val up = push.indexOf("synchronized (lock) { frameGenPushing = true; }")
        assertTrue(up in 0 until push.indexOf("push.run();"))
        val fin = push.indexOf("finally")
        assertTrue(fin > push.indexOf("push.run();"))
        assertTrue(push.indexOf("frameGenPushing = false;") > fin)
        assertTrue(push.indexOf("if (resend) xServerView.queueEvent(this::updateScene);") > fin)
        val list = body(r, "private void pushRenderList(ArrayList<RenderableWindow> list)")
        val held = list.indexOf("if (frameGenPushing) { renderListHeld = true; return; }")
        assertTrue(held in 0 until list.indexOf("nativeSetRenderList("))
        val calls = all(r, "pushRenderList(").filter { !r.startsWith("private void pushRenderList(", it - "private void ".length) }
        assertTrue(calls.isNotEmpty())
        val sceneLock = blocks(r, "synchronized (lock)")
        for (at in calls) assertTrue(sceneLock.any { at in it })
    }

    @Test fun theNativeRendererLifecycleAndFilterUseTheFrameGenerationLock() {
        val r = renderer
        val fg = blocks(r, "synchronized (frameGenLock)")
        for (call in listOf("nativeDestroy(nativeHandle)", "nativeInit(surface", "nativeReattachSurface(nativeHandle", "nativeDetachSurface(nativeHandle)",
                            "nativeSetFilterMode(nativeHandle, mode)")) {
            val at = all(r, call)
            assertTrue(call, at.isNotEmpty())
            for (i in at) assertTrue(call, fg.any { i in it })
        }
        val sceneLock = blocks(r, "synchronized (lock)")
        for (open in fg) assertFalse("lock order must be frameGenLock then scene lock", sceneLock.any { open.first in it })
    }

    @Test fun sceneAndCounterReadsRemainIndependentOfTheSlowPushLock() {
        val r = renderer
        for (head in listOf("public boolean isFrameGenerationSupported()", "public long getPresentedFrameCount()",
                            "public void setSourceFrameCount(long count)")) {
            val b = body(r, head)
            assertTrue(head, b.contains("synchronized (lock)"))
            assertFalse(head, b.contains("frameGenLock"))
        }
    }
}
