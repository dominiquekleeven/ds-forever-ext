package com.jrees.wowcompanion

import java.io.File
import java.lang.reflect.Modifier
import java.util.IdentityHashMap

/** Test-only bounded walk of strong references from companion statics and the main looper. */
object RetainedPath {
    fun find(target: (Any) -> Boolean, maxObjects: Int = 3_000_000): String? {
        val from = IdentityHashMap<Any, Pair<Any?, String>>()
        val queue = ArrayDeque<Any>()
        fun push(o: Any?, parent: Any?, how: String) {
            if (o == null || from.containsKey(o) || o is Class<*> || o is String || o is Number || o is Boolean || o is Char) return
            from[o] = parent to how; queue.addLast(o)
        }
        val dirs = listOf("../dsforever/build/tmp/kotlin-classes/debug", "dsforever/build/tmp/kotlin-classes/debug")
            .map { File(it) }.filter { it.isDirectory }
        for (dir in dirs) dir.walkTopDown().filter { it.isFile && it.extension == "class" }.forEach { file ->
            val name = file.relativeTo(dir).path.removeSuffix(".class").replace(File.separatorChar, '.')
            val c = runCatching { Class.forName(name, false, RetainedPath::class.java.classLoader) }.getOrNull() ?: return@forEach
            for (field in runCatching { c.declaredFields }.getOrDefault(emptyArray())) {
                if (!Modifier.isStatic(field.modifiers) || field.type.isPrimitive) continue
                push(runCatching { field.isAccessible = true; field.get(null) }.getOrNull(), null, "static ${c.name}.${field.name}")
            }
        }
        runCatching { push(android.os.Looper.getMainLooper().queue, null, "main looper queue") }
        var n = 0
        while (queue.isNotEmpty() && n++ < maxObjects) {
            val o = queue.removeFirst()
            if (target(o)) {
                val path = ArrayList<String>()
                var cur: Any? = o
                while (cur != null) { val (parent, how) = from[cur]!!; path.add(0, "$how (${cur.javaClass.name})"); cur = parent }
                return path.joinToString("\n  -> ")
            }
            val c = o.javaClass
            if (c.isArray) {
                if (!c.componentType.isPrimitive) (o as Array<*>).forEachIndexed { i, v -> push(v, o, "[$i]") }
                continue
            }
            if (o is java.lang.ref.Reference<*>) continue
            if (o is java.util.WeakHashMap<*, *>) { runCatching { o.values.toList().forEach { push(it, o, "weak map value") } }; continue }
            if (o is Map<*, *>) { runCatching { o.entries.toList().forEach { (k, v) -> push(k, o, "key"); push(v, o, "value") } }; continue }
            if (o is Collection<*>) { runCatching { o.toList().forEachIndexed { i, v -> push(v, o, "[$i]") } }; continue }
            if (o is java.util.concurrent.atomic.AtomicReference<*>) { push(o.get(), o, "value"); continue }
            if (c.name.startsWith("java.") || c.name.startsWith("jdk.") || c.name.startsWith("sun.")) continue
            var k: Class<*>? = c
            while (k != null) {
                for (field in runCatching { k.declaredFields }.getOrDefault(emptyArray())) {
                    if (Modifier.isStatic(field.modifiers) || field.type.isPrimitive) continue
                    push(runCatching { field.isAccessible = true; field.get(o) }.getOrNull(), o, ".${field.name}")
                }
                k = k.superclass
            }
        }
        return null
    }
}
