package com.jrees.wowcompanion

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowSystemClock
import java.lang.ref.WeakReference
import java.time.Duration

/** Backport of 4319540c: discarded Home pages must not stay in static listener lists. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class HomeViewsReleasedTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private fun lay(v: View) {
        v.measure(View.MeasureSpec.makeMeasureSpec(1240, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1080 - Strip.HEIGHT, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
    }
    private fun settle(n: Int = 40) { repeat(n) { ShadowSystemClock.advanceBy(Duration.ofMillis(16)); ShadowLooper.idleMainLooper() } }

    private fun page(): List<WeakReference<View>> {
        HomeSettings.init(app); Settings.init(app)
        val holder = FrameLayout(app)
        val map = MapView(app); val cast = CastView(app) { }; val chat = ChatView(app) { }
        holder.addView(map); holder.addView(cast); holder.addView(chat)
        val home = HomeView(app, map, cast, chat) { }
        home.borrow(); lay(home)
        cast.update(JSONObject().put("hud", JSONObject().put("ok", true).put("page", 1)
            .put("pages", JSONArray().put(true).put(true).put(true)).put("L", JSONObject().put("n", JSONArray()))))
        home.tick(); settle()
        val strips = listOf("castStrip", "castStrip2").map { name ->
            HomeView::class.java.getDeclaredField(name).apply { isAccessible = true }.get(home) as View
        }
        return (listOf(home, map, cast, chat) + strips).map { WeakReference(it) }
    }

    @Test fun aHomeThatIsGoneIsCollected() {
        val refs = ArrayList<WeakReference<View>>()
        repeat(3) { refs += page() }
        // Named registries such as MemWatch's map source may keep the newest page only.
        page()
        val swing = HomeSettings.swingTimer
        HomeSettings.swingTimer = !swing; HomeSettings.swingTimer = swing
        settle()
        repeat(20) {
            if (refs.any { it.get() != null }) { System.gc(); System.runFinalization(); settle(2); Thread.sleep(50) }
        }
        val kept = refs.mapNotNull { it.get()?.javaClass?.simpleName }
        val held = refs.mapNotNull { it.get() }.toSet()
        val path = if (kept.isEmpty()) null else RetainedPath.find({ it in held })
        assertEquals("discarded views: $kept; retained path: $path", emptyList<String>(), kept)
    }

    @Test fun theCurrentHomeStillTakesSettingsChanges() {
        HomeSettings.init(app); Settings.init(app)
        val before = HomeSettings.rest
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val holder = FrameLayout(controller.get())
            val map = MapView(app); val cast = CastView(app) { }; val chat = ChatView(app) { }
            holder.addView(map); holder.addView(cast); holder.addView(chat)
            val home = HomeView(app, map, cast, chat) { }
            holder.addView(home); controller.get().setContentView(holder)
            home.borrow(); lay(home)
            for (rest in HomeRest.values()) {
                HomeSettings.rest = rest; settle()
                assertEquals(rest, home.rest)
            }
        } finally {
            HomeSettings.rest = before
            controller.pause().stop().destroy()
        }
    }
}
