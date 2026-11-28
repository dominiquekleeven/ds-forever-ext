package com.jrees.wowcompanion

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeMenuOwnershipTest {
    @Test fun androidKeepsGamepadBlockedWithoutShowingTheTouchShield() {
        val src = listOf(java.io.File("../companion/app/src/main/java/com/jrees/wowcompanion"),
            java.io.File("companion/app/src/main/java/com/jrees/wowcompanion")).first { it.isDirectory }
        val host = java.io.File(src, "CompanionHost.kt").readText()
        assertTrue(host.contains("gameMenuLock.locked = padOwner.touchLocked"))
        assertTrue(host.contains("PadHub.gameMenuLocked = padOwner.gmLock"))
        assertTrue(host.contains("if (padOwner.gmLock || PadHub.inputBlocked) return"))
        val pad = java.io.File(src, "PadInput.kt").readText()
        assertTrue(pad.contains("if (menuGate.deliver(neutral())) each(f)"))
    }

    @Test fun auctionBlocksGamepadButAllowsBottomTouch() {
        val owner = PadOwnerSync()
        owner.note(JSONObject("""{"gmLock":true,"gmTouch":false,"routerMode":"room","routerLeave":{"row":"x","col":1}}"""))
        assertTrue(owner.gmLock)
        assertFalse(owner.touchLocked)
        assertEquals(PadOwnerSync.Act.NONE, owner.step(true, 100L).act)
        owner.noteGameMenu(JSONObject("""{"gmLock":true,"gmTouch":true}"""), current = false)
        assertFalse(owner.touchLocked)
        owner.noteGameMenu(JSONObject("""{"gmLock":true,"gmTouch":true}"""))
        assertTrue(owner.touchLocked)
        owner.noteGameMenu(JSONObject("""{"gmLock":true,"gmTouch":false}"""))
        assertTrue(owner.gmLock)
        assertFalse(owner.touchLocked)
        owner.noteGameMenu(JSONObject("""{"gmLock":true}"""))
        assertTrue("old menu messages retain touch safety", owner.touchLocked)
        owner.noteGameMenu(JSONObject("""{"gmLock":false}"""))
        assertFalse(owner.touchLocked)
    }

    @Test fun cancellingHeldRadialsDoesNotPickOrCloseTheirModalState() {
        val doc = RadialModel.Doc(listOf(RadialModel.Item(0, 1, "Item", 1, 1, 0)),
            0, emptySet(), false, "radial", true, false)
        val radial = RadialModel.Flow()
        radial.stick(doc, 0, 100L)
        radial.cancelInput()
        assertEquals(0, radial.pointed)
        assertEquals(RadialModel.Act.None, radial.stick(doc, -1, 200L))
        radial.stick(doc, 0, 300L)
        assertTrue(radial.stick(doc, -1, 500L) is RadialModel.Act.Use)

        val pause = HomePauseModel.Flow()
        pause.vector(0.0, 1.0, 100L)
        val pointed = pause.pointed
        pause.cancelInput()
        assertEquals(pointed, pause.pointed)
        assertEquals(HomePauseModel.Act.None, pause.vector(0.0, 0.0, 200L))

        val chord = ClassChord.Flow()
        chord.chord(ClassChord.LEFT, false, { false }, { false }, 0)
        chord.cancelInput()
        assertEquals(ClassChord.LEFT, chord.side)
        assertEquals(ClassChord.Flow.Act.None, chord.chord(ClassChord.NONE, false, { false }, { false }, -1))
        assertEquals(ClassChord.LEFT, chord.side)
    }

    @Test fun nativeMenuLocksOutsideCombatAndDoesNotReconcileTheRoomAway() {
        val owner = PadOwnerSync()
        owner.note(JSONObject("""{"gmLock":true,"routerMode":"room","routerLeave":{"row":"x","col":1}}"""))
        assertEquals(PadOwnerSync.Act.NONE, owner.step(true, 100L).act)
        owner.noteGameMenu(JSONObject("""{"gmLock":false}"""))
        assertEquals(PadOwnerSync.Act.LEAVE, owner.step(false, 200L).act)
    }

    @Test fun nativeMenuSuspendsFightReconciliationButNotOtherModalRules() {
        val owner = PadOwnerSync()
        owner.note(JSONObject("""{"fightMode":"game","fightEnter":{"row":"x","col":1},"gmLock":true}"""))
        assertEquals(PadOwnerSync.Act.NONE, owner.step(true, 100L).act)
        owner.noteGameMenu(JSONObject("""{"gmLock":false}"""), current = false)
        assertTrue(owner.gmLock)
        owner.noteGameMenu(JSONObject("""{"gmLock":false}"""))
        assertEquals(PadOwnerSync.Act.NONE, owner.step(true, 200L, deathOnly = true).act)
        assertEquals(PadOwnerSync.Act.ENTER, owner.step(true, 300L).act)
    }

    @Test fun menuPressAndReleaseNeverBecomeANewBottomPress() {
        val gate = NativeMenuInputGate()
        assertTrue(gate.deliver(neutral = false))
        assertTrue(gate.menu(true)) // caller sends ONE held-state reset and cancels repeat
        assertFalse(gate.menu(true))
        repeat(4) { assertFalse(gate.deliver(neutral = false)) } // D-pad, face, side, stick
        assertFalse(gate.deliver(neutral = true)) // resting while still in the menu
        gate.menu(false)
        assertFalse(gate.deliver(neutral = false)) // closing B or a held stick
        assertFalse(gate.deliver(neutral = true)) // closing release only rearms
        assertTrue(gate.deliver(neutral = false)) // next fresh press
    }
}
