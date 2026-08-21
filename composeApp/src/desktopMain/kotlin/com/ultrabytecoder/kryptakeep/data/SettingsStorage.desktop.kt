package com.ultrabytecoder.kryptakeep.data

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Properties
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Key/value storage in `<user.home>/.kryptakeep/settings.properties`.
 * Every mutation is flushed synchronously so a crash cannot lose a write that
 * was acknowledged to the caller (same commit()-like contract as the Android
 * SharedPreferences implementation).
 *
 * All instances sharing the same base directory share ONE in-memory
 * [Properties] cache and one lock ([SharedStore]). Without this, two instances
 * (e.g. the DI-injected one used by KeyManager and the lazily created one in
 * SecretCipherBackend) would each keep a stale copy of the file: a full-file
 * rewrite by one instance would silently resurrect keys removed by the other,
 * or delete keys the other just wrote.
 */
actual class SettingsStorage actual constructor(context: Any?) {
    private val baseDir: File = (context as? File) ?: appDataDir()
    private val store: SharedStore = SharedStore.forDir(baseDir)

    actual fun putString(key: String, value: String) = store.putString(key, value)
    actual fun getString(key: String): String? = store.getString(key)
    actual fun remove(key: String) = store.remove(key)

    /**
     * One shared cache + lock per base directory. The map is bounded by the
     * number of distinct directories the app ever opens (1 in production).
     */
    internal class SharedStore(val baseDir: File) {
        private val lock = ReentrantLock()
        private val file = File(baseDir, "settings.properties")
        private val properties = Properties()

        init {
            baseDir.mkdirs()
            restrictToOwner(baseDir)
            if (file.exists()) {
                try {
                    FileInputStream(file).use { input ->
                        properties.load(InputStreamReader(input, StandardCharsets.UTF_8))
                    }
                } catch (_: Exception) {
                    // Corrupt settings file: start fresh (a corrupt settings.properties
                    // must not prevent the app from starting; PIN key material is
                    // written atomically via a temp-file rename below).
                    properties.clear()
                }
            }
        }

        fun putString(key: String, value: String) {
            lock.withLock {
                properties.setProperty(key, value)
                save()
            }
        }

        fun getString(key: String): String? = lock.withLock { properties.getProperty(key) }

        fun remove(key: String) {
            lock.withLock {
                properties.remove(key)
                save()
            }
        }

        /**
         * Atomic write: write to a temp file in the same directory, then rename over
         * the target. A crash mid-write leaves either the old file or the new one,
         * never a truncated mix.
         */
        private fun save() {
            val temp = File(baseDir, "settings.properties.tmp")
            OutputStreamWriter(FileOutputStream(temp), StandardCharsets.UTF_8).use { writer ->
                properties.store(writer, null)
            }
            if (!temp.renameTo(file)) {
                // Fallback for exotic filesystems without atomic rename semantics.
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        }

        companion object {
            private val lock = Any()
            private val instances = mutableListOf<SharedStore>()

            fun forDir(dir: File): SharedStore = synchronized(lock) {
                instances.firstOrNull { it.baseDir == dir } ?: SharedStore(dir).also { instances.add(it) }
            }
        }
    }
}
