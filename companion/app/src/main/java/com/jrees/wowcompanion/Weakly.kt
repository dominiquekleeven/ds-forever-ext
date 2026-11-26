package com.jrees.wowcompanion

/**
 * A static listener must not keep its owner. Once the owner is collected this callback does nothing.
 * [f] must use its argument only: referring directly to the owner's members would capture the owner again.
 */
internal fun <T : Any> weakly(owner: T, f: (T) -> Unit): () -> Unit {
    val ref = java.lang.ref.WeakReference(owner)
    return { ref.get()?.let(f) }
}
