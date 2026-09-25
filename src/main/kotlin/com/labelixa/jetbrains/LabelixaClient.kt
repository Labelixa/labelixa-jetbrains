package com.labelixa.jetbrains

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/** 402/429 are quota or rate limit: callers must be able to tell. */
class LabelixaException(val status: Int, val serverMessage: String, val retryAfter: Int = 0) :
    IOException("HTTP $status: $serverMessage") {
    val quota: Boolean get() = status == 402 || status == 429
}

/**
 * A thin client for the two endpoints the plugin uses.
 *
 * Why not the Java SDK: the IDE bundles its own HTTP stack and a plugin
 * that pulls a second one grows for no benefit. Only two endpoints live
 * here; the repository tests lock the path strings to the SDK's, so a
 * contract drift breaks a test instead of silently rotting the published
 * plugin.
 */
class LabelixaClient(
    apiKey: String?,
    baseUrl: String = Core.DEFAULT_BASE_URL,
    private val version: String = "0.0.0",
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
) {
    private val base = baseUrl.trimEnd('/')
    private val apiKey = apiKey?.trim()?.takeIf { it.isNotEmpty() }

    private fun request(path: String): HttpRequest.Builder {
        val b = HttpRequest.newBuilder(URI.create(base + path))
            .timeout(Duration.ofSeconds(60))
            .header("User-Agent", "labelixa-jetbrains/$version")
            .header("X-Client", "jetbrains/$version")
        if (apiKey != null) b.header("X-API-Key", apiKey)
        return b
    }

    private fun post(path: String, body: String): HttpResponse<ByteArray> {
        val req = request(path)
            .header("Content-Type", "text/plain; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofByteArray())
        if (res.statusCode() != 200) {
            val text = String(res.body(), StandardCharsets.UTF_8).take(500)
            val retry = res.headers().firstValue("Retry-After").orElse("60").toIntOrNull() ?: 60
            throw LabelixaException(res.statusCode(), text, retry)
        }
        return res
    }

    /**
     * Renders a single label to PNG. This CONSUMES label quota, which is why
     * the plugin calls it only when the user explicitly asks, never on a
     * keystroke.
     */
    fun renderPng(zpl: String, dpmm: Int, widthIn: Double, heightIn: Double, index: Int = 0): ByteArray =
        post(Core.renderPath(dpmm, widthIn, heightIn, index), zpl).body()

    /**
     * Lints ZPL; returns the raw JSON report. Does not consume label quota
     * (only the rate limit), so an editor can lint on every save.
     */
    fun diagnostics(zpl: String, dpmm: Int, widthIn: Double, heightIn: Double): String {
        val q = "?dpmm=$dpmm&w=${Core.g(widthIn)}&h=${Core.g(heightIn)}"
        return String(post(Core.DIAGNOSTICS_PATH + q, zpl).body(), StandardCharsets.UTF_8)
    }
}
