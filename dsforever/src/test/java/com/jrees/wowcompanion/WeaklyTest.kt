package com.jrees.wowcompanion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.ref.WeakReference

/** The static listener outlives its owner; live owners still receive exactly one callback per change. */
class WeaklyTest {
    private class Owner(var changes: Int = 0)

    private fun oldOwner(listeners: MutableList<() -> Unit>): WeakReference<Owner> {
        val owner = Owner()
        listeners += weakly(owner) { it.changes++ }
        return WeakReference(owner)
    }

    @Test fun releasedOwnersAreCollectedAndTheCurrentOwnerUpdatesOnce() {
        val listeners = ArrayList<() -> Unit>()
        val old = List(4) { oldOwner(listeners) }
        val current = Owner()
        listeners += weakly(current) { it.changes++ }
        repeat(30) {
            if (old.any { it.get() != null }) { System.gc(); System.runFinalization(); Thread.sleep(20) }
        }
        old.forEach { assertNull("static listener must not keep its owner", it.get()) }
        repeat(3) { listeners.forEach { it() } }
        assertEquals("one callback per change, not one per discarded owner", 3, current.changes)
    }
}
