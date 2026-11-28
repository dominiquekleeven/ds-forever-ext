package com.jrees.wowcompanion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.LruCache
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Fixture is emitted by the real Quests.lua against the pinned QuestieDB API and native TOC rows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuestieMapMarkersTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private fun fixture() = JSONObject(javaClass.getResource("/questie-markers.json")!!.readText())
    private fun map(id: Int = 1429) = JSONObject("""{"ok":true,"mapID":$id,"x":0.48,"y":0.42,"quests":[],
        "me":{"lvl":3,"race":1,"cls":1,"on":[33,15],"done":[783]},
        "rect":{"c":0,"t":1000,"b":-1300,"l":2000,"r":-1500}}""")
    private fun field(o: Any, name: String): Any? = o.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.get(o) }
    private fun set(o: Any, name: String, value: Any) = o.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.set(o, value) }
    private fun call(o: Any, name: String, id: Int): Any? = o.javaClass.getDeclaredMethod(name, Int::class.javaPrimitiveType).let {
        it.isAccessible = true; it.invoke(o, id)
    }
    @Suppress("UNCHECKED_CAST")
    private fun marks(v: WorldMapRoomView, id: Int = 1429) = call(v, "marksOf", id) as List<Any>
    @Suppress("UNCHECKED_CAST")
    private fun available(v: WorldMapRoomView, id: Int = 1429) = call(v, "availableMarks", id) as List<QdbPins.AvailQ>

    private fun focusRoom(quests: JSONObject, live: JSONObject = map()): WorldMapRoomView {
        val v = WorldMapRoomView(app)
        v.model.maps[1415] = WorldMapModel.MapInfo(1415, "Eastern Kingdoms", 2, 0, 1500, 1000, emptyList(),
            world = WorldMapModel.World(0, 10000.0, -10000.0, 10000.0, -10000.0))
        v.model.maps[1429] = WorldMapModel.MapInfo(1429, "Elwynn Forest", 3, 1415, 1500, 1000, emptyList(),
            world = WorldMapModel.World(0, 1000.0, -1300.0, 2000.0, -1500.0))
        v.model.maps[1436] = WorldMapModel.MapInfo(1436, "Westfall", 3, 1415, 1500, 1000, emptyList(),
            world = WorldMapModel.World(0, -1500.0, -4000.0, 5000.0, 2000.0))
        v.update(live); v.onQuests(quests)
        call(v, "jumpTo", 1429)
        return v
    }

    private fun assertCameraAt(v: WorldMapRoomView, map: Int, x: Float, y: Float) {
        val p = v.model.placeOn(map, field(v, "canvas") as Int)!!
        assertEquals(p[0] + x * (p[2] - p[0]), field(v, "flyX") as Float, 0.00001f)
        assertEquals(p[1] + y * (p[3] - p[1]), field(v, "flyY") as Float, 0.00001f)
    }

    @Test fun selectingQuestieQuestsInTheCurrentZoneCentersTheirDrawnPoints() {
        val v = focusRoom(fixture().getJSONObject("quests"))
        for (id in listOf(33, 15)) {
            val marker = marks(v).single { field(it, "id") == id }
            set(v, "selQuest", id); call(v, "followQuest", id)
            assertFalse((field(v, "flyZ") as Float).isNaN())
            assertCameraAt(v, 1429, field(marker, "x") as Float, field(marker, "y") as Float)
        }
    }

    @Test fun questieMapIdWinsOverTheQuestLogHeaderName() {
        val q = JSONObject("""{"list":[{"id":991,"zone":"Elwynn Forest","qdb":{"obj":[{"m":1436,"x":0.2,"y":0.8}]}}]}""")
        val v = focusRoom(q)
        set(v, "selQuest", 991); call(v, "followQuest", 991)
        assertEquals(1436, field(v, "hereTarget"))
        assertCameraAt(v, 1436, 0.2f, 0.8f)
    }

    @Test fun selectionUsesTheNativePointWhenQuestieAlsoHasOne() {
        val live = map().put("quests", JSONArray("""[{"questID":33,"x":0.8,"y":0.2}]"""))
        val v = focusRoom(fixture().getJSONObject("quests"), live)
        set(v, "selQuest", 33); call(v, "followQuest", 33)
        assertCameraAt(v, 1429, 0.8f, 0.2f)
    }

    @Test fun noKnownPointRetainsTheZoneNameFallback() {
        val v = focusRoom(JSONObject("""{"list":[{"id":992,"zone":"Westfall"}]}"""))
        set(v, "selQuest", 992); call(v, "followQuest", 992)
        assertEquals(1436, field(v, "hereTarget"))
        assertCameraAt(v, 1436, 0.5f, 0.5f)
    }

    @Test fun actualDecodersAndBothMapCallersReceiveTheThreeKinds() {
        val f = fixture()
        assertEquals(7, QdbPins.avail(f.getJSONObject("available"))!!.q.single().id)
        assertEquals(33, QdbAreas.parse(f.getJSONObject("areas"))!!.objs.first().qid)
        val q = f.getJSONObject("quests")
        val room = WorldMapRoomView(app).apply { update(map()); onQuests(q); onQdbAvail(f.getJSONObject("available")); onQdbAreas(f.getJSONObject("areas")) }
        assertEquals(listOf(7), available(room).map { it.id })
        assertEquals(setOf(33, 15), marks(room).map { field(it, "id") }.toSet())
        val finisher = marks(room).single { field(it, "id") == 15 }
        assertEquals(true, field(finisher, "done"))
        assertEquals(0.4892f, field(finisher, "x"))
        assertEquals(0.4161f, field(finisher, "y"))
        assertNotNull(QdbAreas.latestFor(1429))
        val home = MapView(app).apply { update(map(), 0); onQuests(q); onQdbAvail(f.getJSONObject("available")); onQdbAreas(f.getJSONObject("areas")) }
        assertFalse((field(home, "qdbAvail") as Map<*, *>).isEmpty())
        val ghosts = field(home, "qdbSpots") as List<*>
        assertTrue(ghosts.any { (it as QdbSpots.Spot).qid == 33 && !it.turnIn })
        assertTrue(ghosts.any { (it as QdbSpots.Spot).qid == 15 && it.turnIn })
    }

    @Test fun nativePointsWinAndMapSwitchAndEmptyDocumentsDoNotLeaveStaleMarks() {
        val f = fixture()
        val room = WorldMapRoomView(app).apply { update(map()); onQuests(f.getJSONObject("quests")); onQdbAvail(f.getJSONObject("available")) }
        val native = map().put("quests", JSONArray("""[{"questID":33,"x":0.6,"y":0.7}]"""))
        room.update(native)
        val objective = marks(room).filter { field(it, "id") == 33 }
        assertEquals(1, objective.size)
        assertEquals(0.6f, field(objective.single(), "x"))
        assertTrue(marks(room, 1436).isEmpty())
        assertTrue(available(room, 1436).isEmpty())
        room.update(map(1436))
        assertTrue(available(room, 1429).isEmpty())
        room.onQuests(JSONObject("""{"list":[]}"""))
        assertTrue(marks(room, 1429).isEmpty())
        room.update(map())
        room.onQdbAvail(JSONObject("""{"m":1429,"set":2,"q":[]}"""))
        assertTrue(available(room).isEmpty())
    }

    @Test fun receiversPreserveFiltersAndTheAvailableRendererUsesTheHomeAtlasCell() {
        val prefs = app.getSharedPreferences("mapfilters", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("q_avail", false).putBoolean("q_other", false).commit()
        val f = fixture()
        val room = WorldMapRoomView(app).apply { update(map()); onQuests(f.getJSONObject("quests")); onQdbAvail(f.getJSONObject("available")) }
        assertTrue(available(room).isEmpty())
        assertFalse(prefs.getBoolean("q_avail", true))
        assertFalse(prefs.getBoolean("q_other", true))
        prefs.edit().putBoolean("q_avail", true).commit()
        // A ready hand-in on this same NPC correctly hides its !; remove it to test the available art itself.
        room.onQuests(JSONObject(f.getJSONObject("quests").toString()).put("list", JSONArray().put(f.getJSONObject("quests").getJSONArray("list").getJSONObject(0))))
        set(room, "shown", 1429)
        @Suppress("UNCHECKED_CAST")
        val cache = field(room, "bmps") as LruCache<String, Bitmap>
        cache.put("quest-icons", Bitmap.createBitmap(31 * 8, 8, Bitmap.Config.ARGB_8888))
        val cells = ArrayList<Rect>()
        val c = object : Canvas(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)) {
            override fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: RectF, paint: Paint?) { if (src != null) cells.add(Rect(src)); super.drawBitmap(bitmap, src, dst, paint) }
        }
        val draw = room.javaClass.getDeclaredMethod("drawAvailable", Canvas::class.java).apply { isAccessible = true }
        draw.invoke(room, c)
        assertEquals(listOf(Rect(18 * 8, 0, 19 * 8, 8)), cells)
        prefs.edit().remove("q_avail").remove("q_other").commit()
    }
}
