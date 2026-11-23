package com.jrees.wowcompanion

/** Launcher-owned driver service. No Android picker or driver files belong to the Canvas UI. */
object GpuDriverMenu {
    data class Driver(val id: String?, val label: String)
    data class State(
        val installed: List<Driver> = emptyList(),
        val selected: String? = null,
        val adreno: Boolean = false,
        val busy: Boolean = false,
        val message: String? = null,
    ) {
        val choices get() = listOf(Driver(null, "Bundled default"), Driver("System", "System driver")) + installed
        val selectedLabel get() = choices.firstOrNull { it.id == selected }?.label ?: "Saved driver unavailable"
    }
    interface Service {
        val state: State
        val enabled: Boolean
        fun refresh()
        fun select(id: String?)
        fun importZip()
    }

    var service: Service? = null
    var launching = false
    var open = false
        private set
    var cursor = 0
        get() = field.coerceIn(0, state.choices.lastIndex)
        private set
    val state get() = service?.state ?: State(message = "Driver settings are unavailable here.")
    val holdsLaunch get() = open || state.busy
    val page get() = cursor / PAGE_SIZE
    val pageCount get() = (state.choices.size + PAGE_SIZE - 1) / PAGE_SIZE
    const val PAGE_SIZE = 3
    const val ROW0 = 100
    const val PREVIOUS = 90
    const val NEXT = 91
    const val IMPORT = 92
    const val BACK = 93

    fun show() {
        if (service == null) return
        open = true
        resetFocus()
        cursor = state.choices.indexOfFirst { it.id == state.selected }.coerceAtLeast(0)
        service?.refresh()
    }
    fun close() { open = false }
    fun enabled(id: Int): Boolean = when (id) {
        BACK -> true
        PREVIOUS -> page > 0
        NEXT -> page + 1 < pageCount
        IMPORT -> service?.enabled == true && !state.busy && state.adreno
        in ROW0 until ROW0 + state.choices.size -> service?.enabled == true && !state.busy && (id - ROW0 < 2 || state.adreno)
        else -> false
    }
    fun press(id: Int) {
        if (!open || !enabled(id)) return
        when (id) {
            BACK -> close()
            PREVIOUS -> { cursor = ((page - 1) * PAGE_SIZE).coerceAtLeast(0); resetFocus() }
            NEXT -> { cursor = (page + 1) * PAGE_SIZE; resetFocus() }
            IMPORT -> service?.importZip()
            else -> { cursor = id - ROW0; service?.select(state.choices[cursor].id) }
        }
    }
    // D-pad browses ONLY. Saving requires A or a completed tap; Back never saves.
    var actionFocus: Int? = null
        private set
    val focus get() = actionFocus ?: (ROW0 + cursor)
    fun move(dx: Int, dy: Int) {
        val actions = listOf(IMPORT, BACK)
        val a = actionFocus
        if (a != null) {
            if (dy < 0) actionFocus = null
            else if (dx != 0) actionFocus = actions[(actions.indexOf(a) + dx).coerceIn(0, 1)]
        } else {
            val next = cursor + dy + dx * PAGE_SIZE
            if (next >= state.choices.size) actionFocus = if (enabled(IMPORT)) IMPORT else BACK
            else cursor = next.coerceAtLeast(0)
        }
    }
    fun resetFocus() { actionFocus = null }
}
