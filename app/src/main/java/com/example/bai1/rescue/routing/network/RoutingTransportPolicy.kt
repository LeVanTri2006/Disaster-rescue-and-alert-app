package com.example.bai1.rescue.routing.network

import com.example.bai1.BuildConfig
import com.example.bai1.rescue.routing.model.GraphDownloadInfo
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/** Android includes deep sleep; the JVM branch permits deterministic ordinary unit testing. */
object RoutingTime {
    private val androidRuntime: Boolean get() = System.getProperty("java.vm.name", "").orEmpty().let { it.contains("Dalvik") || it == "ART" }
    fun elapsedMillis(): Long = if (androidRuntime) android.os.SystemClock.elapsedRealtime() else TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
    fun isMainThread(): Boolean = androidRuntime && android.os.Looper.myLooper() == android.os.Looper.getMainLooper()
}

class RoutingTransportPolicy(private val debug: Boolean = BuildConfig.DEBUG) {
    fun url(raw: String): HttpUrl {
        val url = raw.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid routing URL")
        require(url.username.isEmpty() && url.password.isEmpty() && url.fragment == null) { "URL credentials/fragments are forbidden" }
        require(url.isHttps || (BuildConfig.DEBUG && debug && url.scheme == "http" && url.host in DEBUG_HOSTS)) { "Routing URL transport is forbidden" }
        return url
    }
    companion object {
        private val DEBUG_HOSTS: Set<String> = setOf("10.0.2.2", "127.0.0.1", "localhost")
        const val MAX_URL_TTL_SECONDS = 900L
        fun ttl(value: Long) { require(value in 1..MAX_URL_TTL_SECONDS) { "Invalid download TTL" } }
        fun expired(info: GraphDownloadInfo, now: Long): Boolean {
            ttl(info.expiresIn)
            val stamp = info.receivedAtElapsedMillis
            return stamp < 0 || now < stamp || now - stamp >= info.expiresIn * 1000
        }
        fun content(response: Response, api: Boolean) {
            require(response.headers.values("Content-Type").size <= 1) { "Ambiguous content type" }
            require(response.headers.values("Content-Encoding").size <= 1) { "Ambiguous content encoding" }
            val type = (response.header("Content-Type") ?: response.body?.contentType()?.toString())
                ?.substringBefore(';')?.trim()?.lowercase()
            require(if (api) type == "application/json" else type == "application/gzip") { "Unexpected routing content type" }
            val encoding = response.header("Content-Encoding")
            require(encoding == null || encoding.trim().equals("identity", true)) { "Encoded routing response is forbidden" }
            require(response.headers.values("Content-Length").size <= 1) { "Ambiguous content length" }
            response.header("Content-Length")?.let { value ->
                require(value.matches(Regex("^[0-9]+$")) && value.toLongOrNull() != null) { "Invalid content length" }
            }
            val transfer = response.header("Transfer-Encoding")
            require(transfer == null || transfer.trim().equals("chunked", true)) { "Unsupported transfer encoding" }
            require(transfer == null || response.header("Content-Length") == null) { "Conflicting transport lengths" }
        }
    }
}

/** Optional lower limits for constrained callers/tests; no caller can raise the production ceilings. */
data class RoutingResourceLimits(
    val compressedBytes: Long = MAX_COMPRESSED,
    val uncompressedBytes: Long = MAX_UNCOMPRESSED,
    val apiBytes: Long = MAX_API
) {
    init {
        require(compressedBytes in 1..MAX_COMPRESSED && uncompressedBytes in 1..MAX_UNCOMPRESSED && apiBytes in 1..MAX_API)
    }
    companion object {
        const val MAX_COMPRESSED = 8L * 1024 * 1024
        const val MAX_UNCOMPRESSED = 64L * 1024 * 1024
        const val MAX_API = 1024L * 1024
        const val MAX_DEPTH = 32
        const val MAX_STRING_BYTES = 64 * 1024
        const val MAX_LITERAL_BYTES = 128
    }
}

class RoutingLimitException : IOException("Routing resource limit exceeded")

class RoutingProcessingBudget {
    private val started = System.nanoTime()
    fun check() { if (System.nanoTime() - started >= TimeUnit.SECONDS.toNanos(180)) throw RoutingLimitException() }
}

/** Bounded lexical guard on the SAME bytes consumed by the JSON parser; never buffers the graph. */
class RoutingBoundedInputStream(
    input: InputStream,
    private val maximum: Long,
    private val json: Boolean = false,
    private val budget: RoutingProcessingBudget = RoutingProcessingBudget()
) : FilterInputStream(input) {
    private var bytes = 0L
    private var depth = 0
    private var quoted = false
    private var escaped = false
    private var scalar = 0
    private fun accept(value: Int) {
        budget.check()
        if (bytes >= maximum) throw RoutingLimitException()
        bytes++
        if (!json) return
        if (quoted) {
            if (!escaped && value == 34) { quoted = false; scalar = 0; return }
            if (++scalar > RoutingResourceLimits.MAX_STRING_BYTES) throw RoutingLimitException()
            if (escaped) escaped = false else if (value == 92) escaped = true
        } else when (value) {
            34 -> { quoted = true; scalar = 0 }
            123, 91 -> { scalar = 0; if (++depth > RoutingResourceLimits.MAX_DEPTH) throw RoutingLimitException() }
            125, 93 -> { depth--; scalar = 0 }
            32, 9, 10, 13, 44, 58 -> scalar = 0
            else -> if (++scalar > RoutingResourceLimits.MAX_LITERAL_BYTES) throw RoutingLimitException()
        }
    }
    override fun available(): Int = minOf(super.available().toLong(), maxOf(0, maximum - bytes)).toInt()
    override fun markSupported() = false
    override fun reset(): Unit = throw IOException("Bounded routing stream cannot reset")
    override fun skip(count: Long): Long {
        val buffer = ByteArray(8192)
        var remaining = maxOf(0, count)
        while (remaining > 0) {
            val read = read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
            if (read < 0) break
            remaining -= read
        }
        return maxOf(0, count) - remaining
    }
    override fun read(): Int {
        val value = `in`.read()
        if (value >= 0) accept(value)
        return value
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = `in`.read(buffer, offset, length)
        for (i in offset until offset + maxOf(count, 0)) accept(buffer[i].toInt() and 255)
        return count
    }
}
