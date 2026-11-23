package com.jrees.wowcompanion

import android.graphics.Canvas
import android.graphics.RectF
import android.view.KeyEvent

/**
 * THE MENU (the user 2026-11-01), on the waiting screen ([StartView]) and the chooser ([ChooserPanel]): Auto login's tick and Change login,
 * Forget login, Report issue, the controller's two settings; on the waiting screen also Stream settings or Restart game, Switch mode and
 * Exit game. The Menu button turned into an X, B or a touch outside shuts it. The host fills in the actions; one app, one menu. Main thread.
 */
object StartMenu {
    /** the Menu shows; shutting it shuts the region list / card with no change */
    var open = false
        set(v) { field = v; if (!v) { picker.close(); GpuDriverMenu.close() } }
    var onReport: (() -> Unit)? = null
    var autoLogin: () -> Boolean = { true }
    var onAutoLogin: ((Boolean) -> Unit)? = null
    var onChangeLogin: (() -> Unit)? = null
    /** Exit game answers only while a game or a stream runs */
    var canExit: () -> Boolean = { false }
    var onExit: (() -> Unit)? = null
    var canForget: () -> Boolean = { false }
    var onForgetLogin: (() -> Unit)? = null
    var canRestart: () -> Boolean = { false }
    var onRestart: (() -> Unit)? = null
    var canSwitch: () -> Boolean = { false }
    var onSwitch: (() -> Unit)? = null
    /** the game's region (GameRegion's code) and its change (the user, 2026-11-07: "There should be a change region option in the menu
     *  when starting the app"). The bottom row's Region button opens the list ([picker]; the user 2026-11-17: each press of the old
     *  button saved the next region at once and its build downloaded); [onRegion] is called ONLY by the card's Switch. The portal is
     *  written at the game's next start */
    var region: () -> String = { "us" }
    var onRegion: ((String) -> Unit)? = null
    /** the region list and its confirm card (pure); open while the Menu shows it in the panel's place */
    val picker = RegionPicker()
    /** the card asks what switching to a region would do (the launcher compares that region's build with the install's, off the main
     *  thread); the answer comes back through the callback, on any thread. Null = no such check (the card warns of a download) */
    var regionPreview: ((code: String, answer: (RegionPicker.Preview) -> Unit) -> Unit)? = null
    private var forgetArmedAt = -FORGET_ARM_MS
    private var menuCursor = AUTO
    private var menuPlaces: List<Triple<Int, Float, Float>> = emptyList()

    /**
     * the panel centred in [area]; [menuButton] = where the Menu button is, the X now; [hits] gets what a touch or the cursor can press,
     * [pressed] the id held down. [game] = the waiting screen, with what acts on a running game or mode (Stream settings or Restart, Switch mode, Exit game)
     */
    fun draw(c: Canvas, area: RectF, pressed: Int, hits: ArrayList<Pair<RectF, Int>>, menuButton: RectF, game: Boolean) {
        hits.clear()
        if (GpuDriverMenu.open) {
            GpuDriverPanel.draw(c, area, pressed, hits)
            UiKit.button(c, menuButton, "X", StartView.GLYPH_B, if (pressed == CLOSE) UiKit.Button.DOWN else UiKit.Button.UP)
            hits += RectF(menuButton) to CLOSE
            return
        }
        if (picker.open) { drawPicker(c, area, pressed, hits, menuButton); return }
        val rows = if (game) 4 else 3
        val scale = minOf(1f, area.height() / (PAD * 2 + StartView.BUTTON_H * rows + CONTROLLER_H + StartView.GAP * rows))
        val bh = StartView.BUTTON_H * scale; val gap = StartView.GAP * scale
        val pad = PAD * scale; val controllerH = CONTROLLER_H * scale
        val height = pad * 2 + bh * rows + controllerH + gap * rows
        val top = (area.centerY() - height / 2f).coerceAtLeast(area.top)
        val panel = RectF(area.left, top, area.right, top + height)
        UiKit.module(c, panel)
        val inL = panel.left + pad; val inR = panel.right - pad
        val mid = (inL + inR) / 2f
        var y = panel.top + pad
        fun half(right: Boolean, h2: Float = bh) = RectF(if (right) mid + gap / 2f else inL, y, if (right) inR else mid - gap / 2f, y + h2)
        fun down(id: Int) = if (pressed == id) UiKit.Button.DOWN else UiKit.Button.UP
        fun put(r: RectF, word: String, id: Int, on: Boolean) {
            if (!on) { UiKit.button(c, r, word, UiKit.Button.DISABLED); return }
            UiKit.button(c, r, word, down(id))
            hits += r to id
        }
        UiKit.button(c, menuButton, "X", StartView.GLYPH_B, down(CLOSE))
        hits += RectF(menuButton) to CLOSE
        val auto = half(false)
        UiKit.check(c, auto.left + CHECK * scale / 2f, auto.centerY(), CHECK * scale, autoLogin())
        val words = UiKit.bodyPaint(StartView.LINE_SIZE)
        UiKit.outlined(c, "Auto login", (auto.left + CHECK + auto.right) / 2f, auto.centerY() - (words.ascent() + words.descent()) / 2f, StartView.LINE_SIZE, Theme.text)
        hits += auto to AUTO
        put(half(true), "Change login", CHANGE, true)
        y += bh + gap
        val armed = android.os.SystemClock.uptimeMillis() - forgetArmedAt < FORGET_ARM_MS
        put(half(false), if (armed) "Tap again" else "Forget login", FORGET, canForget())
        if (game) {
            if (SetupBridge.streamSettings != null) put(half(true), "Stream settings", STREAM, true)
            else put(half(true), "Restart game", RESTART, canRestart())
            y += bh + gap
            put(half(false), "Report issue", REPORT, true)
            put(half(true), "Switch mode", SWITCH, canSwitch())
        } else put(half(true), "Report issue", REPORT, true)
        y += bh + gap
        // the controller, one object: its two buttons on the left, what they make on the right
        val box = RectF(inL, y, inR, y + controllerH)
        UiKit.border(c, box)
        val inner = RectF(box.left + INSET, box.top + INSET, box.left + box.width() * 0.62f, box.bottom - INSET)
        val rowH = (inner.height() - gap) / 2f
        put(RectF(inner.left, inner.top, inner.right, inner.top + rowH), "ABXY: ${CastSettings.abxyLocation.label}", ABXY, true)
        put(RectF(inner.left, inner.bottom - rowH, inner.right, inner.bottom), "Glyphs: ${CastSettings.buttonLayout.label}", GLYPHS, true)
        faceDiamond(c, RectF(inner.right, box.top, box.right, box.bottom))
        y += controllerH + gap
        // the bottom row: Region (both screens), and Exit game beside it on the waiting screen
        val rg = GameRegion.ALL.firstOrNull { it.code == region() } ?: GameRegion.ALL[0]
        val rWord = "${Tr.t("Region")}: ${Tr.t(rg.label)}"
        if (game) {
            val w = (inR - inL - gap * 2) / 3f
            put(RectF(inL, y, inL + w, y + bh), rWord, REGION, true)
            put(RectF(inL + w + gap, y, inR - w - gap, y + bh), "GPU driver", GPU_DRIVER, GpuDriverMenu.service != null)
            put(RectF(inR - w, y, inR, y + bh), "Exit game", EXIT, canExit())
        } else {
            put(half(false), rWord, REGION, true)
            put(half(true), "GPU driver", GPU_DRIVER, GpuDriverMenu.service != null)
        }
        menuPlaces = hits.map { (r, id) -> Triple(id, r.centerX(), r.centerY()) }
        if (menuPlaces.none { it.first == menuCursor }) menuCursor = hits.getOrNull(1)?.second ?: CLOSE
    }

    /**
     * THE REGION LIST and THE CONFIRM CARD in the panel's place (the user, 2026-11-17). The list: every region, the one in use ticked,
     * the cursor's row boxed by the host ([focusId]). The card: "Switch to EU?", what it does ("Downloads the EU game" / "Uses this
     * install"), Cancel and Switch (Cancel focused first).
     */
    private fun drawPicker(c: Canvas, area: RectF, pressed: Int, hits: ArrayList<Pair<RectF, Int>>, menuButton: RectF) {
        val bh = StartView.BUTTON_H; val gap = StartView.GAP
        fun down(id: Int) = if (pressed == id) UiKit.Button.DOWN else UiKit.Button.UP
        UiKit.button(c, menuButton, "X", StartView.GLYPH_B, down(CLOSE))
        hits += RectF(menuButton) to CLOSE
        val title = UiKit.titlePaint(TITLE_SIZE).apply { textAlign = android.graphics.Paint.Align.CENTER }
        if (picker.stage == RegionPicker.Stage.LIST) {
            val n = picker.regions.size
            val height = PAD * 2 + TITLE_H + bh * n + gap * (n - 1)
            val top = (area.centerY() - height / 2f).coerceAtLeast(area.top)
            val panel = RectF(area.left, top, area.right, top + height)
            UiKit.module(c, panel)
            c.drawText(Tr.t("Region"), panel.centerX(), panel.top + PAD + TITLE_H * 0.62f, title)
            var y = panel.top + PAD + TITLE_H
            for ((i, code) in picker.regions.withIndex()) {
                val r = RectF(panel.left + PAD, y, panel.right - PAD, y + bh)
                UiKit.button(c, r, label(code), down(ROW0 + i))
                if (code == picker.current) UiKit.check(c, r.left + CHECK / 2f + 14f, r.centerY(), CHECK * 0.8f, true)
                hits += r to ROW0 + i
                y += bh + gap
            }
            return
        }
        val t = picker.target ?: return
        val height = PAD * 2 + TITLE_H + LINE_H + gap + bh
        val top = (area.centerY() - height / 2f).coerceAtLeast(area.top)
        val panel = RectF(area.left, top, area.right, top + height)
        UiKit.module(c, panel)
        c.drawText(Tr.t("Switch to ${Tr.t(label(t))}?"), panel.centerX(), panel.top + PAD + TITLE_H * 0.62f, title)
        UiKit.outlined(c, previewLine(t, picker.preview), panel.centerX(), panel.top + PAD + TITLE_H + LINE_H / 2f, StartView.LINE_SIZE, Theme.text)
        val y = panel.top + PAD + TITLE_H + LINE_H + gap
        val mid = panel.centerX()
        val cancel = RectF(panel.left + PAD, y, mid - gap / 2f, y + bh)
        val switch = RectF(mid + gap / 2f, y, panel.right - PAD, y + bh)
        UiKit.button(c, cancel, "Cancel", down(REGION_CANCEL)); hits += cancel to REGION_CANCEL
        UiKit.button(c, switch, "Switch", down(REGION_SWITCH)); hits += switch to REGION_SWITCH
    }

    /** a region's name as the list shows it (English: the drawing translates) */
    fun label(code: String): String = (GameRegion.ALL.firstOrNull { it.code == code } ?: GameRegion.ALL[0]).label

    /** the card's second line for the region [code] (English: the drawing translates); terse. Pure */
    fun previewLine(code: String, p: RegionPicker.Preview): String = when (p) {
        RegionPicker.Preview.CHECKING -> "Checking..."
        RegionPicker.Preview.INSTALL -> "Uses this install"
        // no answer (no network): the plain warning, as a download may follow
        RegionPicker.Preview.DOWNLOAD, RegionPicker.Preview.UNKNOWN -> "Downloads the ${Tr.t(label(code))} game"
    }

    /** the hit the pad's cursor is on while the list or card shows (the host boxes it); null = the Menu's own cursor */
    fun focusId(): Int? = if (GpuDriverMenu.open) GpuDriverMenu.focus else when (picker.stage) {
        RegionPicker.Stage.LIST -> ROW0 + picker.cursor
        RegionPicker.Stage.CONFIRM -> if (picker.switchFocused) REGION_SWITCH else REGION_CANCEL
        RegionPicker.Stage.CLOSED -> menuCursor
    }

    /** One controller path for the chooser and the local waiting menu. Releases/repeats are consumed without a second action. */
    fun key(code: Int, down: Boolean): Boolean {
        if (!open || code !in listOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)) return false
        if (!down) return true
        when (code) {
            KeyEvent.KEYCODE_BUTTON_B -> if (!back()) open = false
            KeyEvent.KEYCODE_BUTTON_A -> if (!pickerA()) press(menuCursor)
            else -> {
                val dx = when (code) { KeyEvent.KEYCODE_DPAD_LEFT -> -1; KeyEvent.KEYCODE_DPAD_RIGHT -> 1; else -> 0 }
                val dy = when (code) { KeyEvent.KEYCODE_DPAD_UP -> -1; KeyEvent.KEYCODE_DPAD_DOWN -> 1; else -> 0 }
                if (!pickerMove(dx, dy)) menuPlaces.firstOrNull { it.first == menuCursor }?.let { (_, x, y) ->
                    nearest(menuPlaces, x, y, dx, dy)?.let { menuCursor = it }
                }
            }
        }
        return true
    }

    /** the D-pad while the list or card shows (true = taken) */
    val submenuOpen get() = picker.open || GpuDriverMenu.open
    fun pickerMove(dx: Int, dy: Int): Boolean {
        if (GpuDriverMenu.open) { GpuDriverMenu.move(dx, dy); return true }
        if (!picker.open) return false; picker.move(dx, dy); return true
    }

    /** A while the list or card shows (true = taken) */
    fun pickerA(): Boolean {
        if (GpuDriverMenu.open) { GpuDriverMenu.press(GpuDriverMenu.focus); return true }
        if (!picker.open) return false; handle(picker.a()); return true
    }

    /** B: one step back in the list / card (no change), true = taken; false = nothing of the region's open (the Menu shuts) */
    fun back(): Boolean {
        if (GpuDriverMenu.open) { GpuDriverMenu.close(); return true }
        if (!picker.open) return false; handle(picker.b()); return true
    }

    private fun handle(r: RegionPicker.Result) {
        when (r) {
            is RegionPicker.Result.Ask -> {
                val ask = regionPreview
                if (ask == null) picker.preview = RegionPicker.Preview.UNKNOWN
                else ask(r.code) { p -> if (picker.target == r.code) picker.preview = p }
            }
            // the one change: the Menu shuts on purpose, then it is saved (a new build may download: the setup page says so, with Stop)
            is RegionPicker.Result.Apply -> { open = false; onRegion?.invoke(r.code) }
            RegionPicker.Result.Closed, RegionPicker.Result.None -> {}
        }
    }

    /** the four face buttons where they sit: Nintendo's X top and A right, Xbox's Y top and B right */
    private fun faceDiamond(c: Canvas, r: RectF) {
        val cx = r.centerX(); val cy = r.centerY()
        val scale = minOf(1f, r.height() / CONTROLLER_H)
        val nintendo = CastSettings.abxyLocation == CastSettings.AbxyLocation.NINTENDO
        UiKit.pad(c, if (nintendo) "rev_x" else "rev_y", cx, cy - ARM * scale, FACE * scale)
        UiKit.pad(c, if (nintendo) "rev_a" else "rev_b", cx + ARM * scale, cy, FACE * scale)
        UiKit.pad(c, if (nintendo) "rev_b" else "rev_a", cx, cy + ARM * scale, FACE * scale)
        UiKit.pad(c, if (nintendo) "rev_y" else "rev_x", cx - ARM * scale, cy, FACE * scale)
    }

    /** the cursor's next place from ([x], [y]) on a D-pad press ([dx], [dy] = its direction, one of them 0): the nearest in that direction,
     *  off to the side counting double; null = nothing there */
    fun nearest(places: List<Triple<Int, Float, Float>>, x: Float, y: Float, dx: Int, dy: Int): Int? = places
        .filter { (_, px, py) -> (px - x) * dx + (py - y) * dy > 1f }
        .minByOrNull { (_, px, py) ->
            val along = kotlin.math.abs((px - x) * dx + (py - y) * dy)
            val side = kotlin.math.abs((px - x) * dy) + kotlin.math.abs((py - y) * dx)
            along + side * 2f
        }?.first

    private fun <T> next(all: Array<T>, now: T): T = all[(all.indexOf(now) + 1) % all.size]

    fun press(id: Int) {
        if (GpuDriverMenu.open && id != CLOSE) { GpuDriverMenu.press(id); return }
        if (!submenuOpen && menuPlaces.any { it.first == id }) menuCursor = id
        when (id) {
            CLOSE -> open = false
            ABXY -> CastSettings.abxyLocation = next(CastSettings.AbxyLocation.values(), CastSettings.abxyLocation)
            GLYPHS -> CastSettings.buttonLayout = next(CastSettings.ButtonLayout.values(), CastSettings.buttonLayout)
            AUTO -> onAutoLogin?.invoke(!autoLogin())
            FORGET -> {
                val at = android.os.SystemClock.uptimeMillis()
                if (at - forgetArmedAt < FORGET_ARM_MS) { forgetArmedAt = -FORGET_ARM_MS; onForgetLogin?.invoke() } else forgetArmedAt = at
            }
            CHANGE -> { open = false; onChangeLogin?.invoke() }
            STREAM -> { open = false; SetupBridge.openStreamSettings?.invoke() }
            RESTART -> { open = false; onRestart?.invoke() }
            SWITCH -> { open = false; onSwitch?.invoke() }
            REPORT -> { open = false; onReport?.invoke() }
            EXIT -> { open = false; onExit?.invoke() }
            // the list, the cursor on the region in use: nothing changes until the card's Switch (the user, 2026-11-17)
            REGION -> picker.open(region())
            GPU_DRIVER -> GpuDriverMenu.show()
            REGION_CANCEL -> handle(picker.cancel())
            REGION_SWITCH -> handle(picker.switch())
            in ROW0 until ROW0 + picker.regions.size -> handle(picker.pick(id - ROW0))
        }
    }

    private const val AUTO = 4
    private const val CHANGE = 5
    private const val REPORT = 6
    private const val EXIT = 7
    private const val FORGET = 8
    private const val STREAM = 9
    private const val RESTART = 10
    private const val SWITCH = 11
    private const val CLOSE = 12
    private const val ABXY = 13
    private const val GLYPHS = 14
    const val REGION = 15
    const val REGION_CANCEL = 16
    const val REGION_SWITCH = 17
    const val GPU_DRIVER = 18
    /** the list's rows: ROW0 + the region's index in GameRegion.ALL */
    const val ROW0 = 20
    private const val TITLE_SIZE = 52f
    private const val TITLE_H = 90f
    private const val LINE_H = 70f
    private const val FORGET_ARM_MS = 4000L
    const val WIDTH = 1000f
    private const val PAD = 32f
    private const val CHECK = 76f
    private const val CONTROLLER_H = 270f
    private const val INSET = 18f
    private const val FACE = 72f
    private const val ARM = 72f
}
