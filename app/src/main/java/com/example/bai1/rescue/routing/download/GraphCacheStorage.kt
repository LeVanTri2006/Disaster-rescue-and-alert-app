package com.example.bai1.rescue.routing.download

import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/** Same identifier grammar as the backend contract; never normalize an untrusted identifier. */
object GraphCacheIdentity {
    private val version = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$")
    fun requireValid(value: String): String {
        require(version.matches(value)) { "Invalid graphVersion" }
        return value
    }
}

/** Fault observation points only: no validation/authorization can be overridden by the hook. */
enum class CacheWritePoint { AFTER_DOWNLOAD, AFTER_DECOMPRESSION, BEFORE_GENERATION_MOVE, BEFORE_POINTER_COMMIT }

/**
 * App-private cache namespace. Generations are never modified or reclaimed by refresh.
 * All instances in this process share a root lock. Atomic pointer replacement also keeps
 * independent processes from observing mixed generations; clearCache requires quiescent readers.
 */
internal class GraphCacheStorage(storageDir: File) {
    private val configuredRoot = storageDir.absoluteFile.toPath().normalize()
    private val root = storageDir.canonicalFile.toPath()
    val lock: ReentrantLock = locks.computeIfAbsent(root.toString()) { ReentrantLock() }
    private val generationName = Regex("^g-[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$")

    fun checked(file: File): File {
        if (Files.isSymbolicLink(configuredRoot) || configuredRoot.toFile().canonicalFile.toPath() != root) {
            throw IOException("Cache root changed or is a symlink")
        }
        val path = file.absoluteFile.toPath().normalize()
        if (!path.startsWith(root)) throw IOException("Cache path escapes owned root")
        var part = root
        for (name in root.relativize(path)) {
            part = part.resolve(name)
            if (Files.isSymbolicLink(part)) throw IOException("Symlink in cache path")
        }
        if (!file.canonicalFile.toPath().startsWith(root)) throw IOException("Canonical cache escape")
        return path.toFile()
    }

    private fun namespace(): File = checked(root.resolve("graph-cache-v2").toFile())
    fun versionDir(version: String, create: Boolean = false): File {
        GraphCacheIdentity.requireValid(version)
        val dir = checked(File(namespace(), version))
        if (create) {
            Files.createDirectories(dir.toPath())
            checked(dir)
        }
        return dir
    }

    fun child(parent: File, name: String): File {
        require(!name.contains('/') && !name.contains('\\') && name != "." && name != "..")
        return checked(File(checked(parent), name))
    }

    fun active(version: String): File? {
        val parent = versionDir(version)
        val pointer = child(parent, "current")
        if (!pointer.isFile || pointer.length() !in 1L..80L) return null
        val name = pointer.readText(Charsets.US_ASCII)
        if (!generationName.matches(name)) return null
        val dir = child(parent, name)
        return dir.takeIf { Files.isDirectory(it.toPath(), LinkOption.NOFOLLOW_LINKS) }
    }

    fun stage(version: String): File {
        val dir = child(versionDir(version, true), ".stage-${UUID.randomUUID()}")
        Files.createDirectory(dir.toPath())
        return checked(dir)
    }

    fun commit(version: String, staging: File, checkpoint: (CacheWritePoint) -> Unit): File {
        checkpoint(CacheWritePoint.BEFORE_GENERATION_MOVE)
        val parent = versionDir(version)
        val generation = child(parent, "g-${UUID.randomUUID()}")
        Files.move(checked(staging).toPath(), generation.toPath(), StandardCopyOption.ATOMIC_MOVE)
        val pending = child(parent, ".pointer-${UUID.randomUUID()}")
        try {
            Files.newOutputStream(pending.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use {
                it.write(generation.name.toByteArray(Charsets.US_ASCII))
            }
            checkpoint(CacheWritePoint.BEFORE_POINTER_COMMIT)
            val current = child(parent, "current")
            // This is the only commit point. No non-atomic fallback is permitted.
            Files.move(checked(pending).toPath(), current.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            return generation
        } finally {
            Files.deleteIfExists(checked(pending).toPath())
        }
    }

    fun cleanupStage(stage: File) {
        if (stage.name.startsWith(".stage-")) deleteTree(stage)
    }

    fun clear(version: String?) {
        if (version != null) deleteTree(versionDir(version)) else deleteTree(namespace())
    }

    private fun deleteTree(file: File) {
        val path = checked(file).toPath()
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
        // walkFileTree does not follow symlinks. Delete the link itself, never its target.
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (!file.normalize().startsWith(root)) throw IOException("Cleanup escape")
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                if (exc != null) throw exc
                checked(dir.toFile())
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    companion object {
        private val locks = ConcurrentHashMap<String, ReentrantLock>()
    }
}
