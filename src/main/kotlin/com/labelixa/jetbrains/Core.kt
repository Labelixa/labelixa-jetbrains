package com.labelixa.jetbrains

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.URISyntaxException
import kotlin.math.abs
import kotlin.math.floor

/**
 * The CORE of the plugin: every decision that does not need the IDE.
 *
 * This file does not import anything from the IntelliJ platform, on
 * purpose: which label the caret is in, which editor range a server finding
 * maps to, whether a quick-fix is usable — all of it lives here and is
 * covered by plain unit tests. The action, annotator and tool window
 * classes are glue only and make no decisions of their own.
 *
 * The same rules are implemented in the VS Code extension; keeping them
 * identical is deliberate, so both editors show the same label and the
 * same findings for the same text.
 */
object Core {
    const val DEFAULT_BASE_URL = "https://api.labelixa.com"

    /** Path templates shared with the SDKs (the repository tests lock them). */
    fun renderPath(dpmm: Int, widthIn: Double, heightIn: Double, index: Int): String =
        "/v1/printers/${dpmm}dpmm/labels/${g(widthIn)}x${g(heightIn)}/$index"
    const val DIAGNOSTICS_PATH = "/v1/diagnostics"

    // ------------------------------------------------------ key and base URL --

    /**
     * Hosts the API key is sent to without an opt-in: the hosted API and the
     * site (the same list as the VS Code extension). Any other address is
     * somebody's own installation or a proxy; the key goes there only after
     * the user has opted in for that exact origin in the settings.
     */
    val TRUSTED_KEY_HOSTS = setOf("api.labelixa.com", "labelixa.com", "staging.labelixa.com")

    /**
     * - TRUSTED: https on a Labelixa host with the default port.
     * - OPT_IN: another usable base URL (another https host, or plain http on
     *   this machine) — the key goes only to the origin the user opted in to.
     * - REFUSE: not a usable base URL (see [baseUrlProblem]); nothing is sent.
     */
    enum class KeyPolicy { TRUSTED, OPT_IN, REFUSE }

    fun isLoopback(host: String): Boolean {
        val h = host.lowercase().removePrefix("[").removeSuffix("]")
        return h == "localhost" || h == "::1" || Regex("^127(\\.\\d{1,3}){3}$").matches(h)
    }

    private fun parse(baseUrl: String): URI? = try {
        URI(baseUrl.trim())
    } catch (e: URISyntaxException) {
        null
    }

    /**
     * Why `baseUrl` must not be used, or `null` when it is fine.
     *
     * Plain http is accepted only for this machine: anywhere else the label
     * text (and the API key) would travel readable on the network. A URL
     * with user info is refused too — `https://api.labelixa.com@evil.test`
     * reads like the API but talks to another host.
     */
    fun baseUrlProblem(baseUrl: String): String? {
        val u = parse(baseUrl)
        val host = u?.host
        if (u == null || u.scheme == null || host.isNullOrEmpty()) {
            return "The base URL \"$baseUrl\" is not a valid URL."
        }
        if (u.rawUserInfo != null) return "The base URL must not contain a user name or password."
        val scheme = u.scheme.lowercase()
        if (scheme == "https") return null
        if (scheme == "http" && isLoopback(host)) return null
        return "The base URL must use https (plain http is allowed only for localhost)."
    }

    fun keyPolicy(baseUrl: String): KeyPolicy {
        if (baseUrlProblem(baseUrl) != null) return KeyPolicy.REFUSE
        val u = parse(baseUrl)!!
        return if (u.scheme.lowercase() == "https" && u.host.lowercase() in TRUSTED_KEY_HOSTS && u.port == -1) {
            KeyPolicy.TRUSTED
        } else {
            KeyPolicy.OPT_IN
        }
    }

    /** `scheme://host[:port]`, lower case; "" when not a URL. The opt-in is stored per origin. */
    fun keyOrigin(baseUrl: String): String {
        val u = parse(baseUrl) ?: return ""
        val scheme = u.scheme?.lowercase() ?: return ""
        val host = u.host?.lowercase() ?: return ""
        return if (u.port == -1) "$scheme://$host" else "$scheme://$host:${u.port}"
    }

    /**
     * Is the API key sent to `baseUrl`? `optedInOrigin` is the origin the
     * user explicitly allowed in the settings. Changing the base URL to
     * another origin does not carry the opt-in over.
     */
    fun sendKey(baseUrl: String, optedInOrigin: String): Boolean = when (keyPolicy(baseUrl)) {
        KeyPolicy.TRUSTED -> true
        KeyPolicy.OPT_IN -> optedInOrigin.isNotEmpty() && optedInOrigin == keyOrigin(baseUrl)
        KeyPolicy.REFUSE -> false
    }

    /** Python `:g` formatting: 4.0 -> "4", 2.25 -> "2.25" (same rule as the SDKs). */
    fun g(n: Double): String =
        if (n == floor(n) && abs(n) < 1e15) n.toLong().toString() else n.toString()

    // ------------------------------------------------------- caret's block --

    data class Block(val start: Int, val end: Int, val closed: Boolean)

    /**
     * Returns the ^XA…^XZ blocks in the text with their source offsets.
     *
     * Two rules match the rendering engine, both deliberate:
     *
     * 1. A second ^XA RESTARTS the block. `^XA…^XA…^XZ` is one block whose
     *    start is the LAST ^XA. Treating the first ^XA as the start would
     *    send a preview body containing commands the printer never prints.
     * 2. The caret is fixed (`^`). The engine's tokenizer works the same way
     *    and does not support changing the caret with ^CC.
     *
     * One deliberate difference: an unterminated last block. The engine
     * drops it (nothing is emitted before ^XZ). Here it is returned with
     * `closed = false` instead: the user may still be typing the label, and
     * answering "nothing here" would leave the preview silently dead while
     * writing. The caller sees the flag and says "^XZ missing".
     */
    fun labelBlocks(text: String): List<Block> {
        val out = ArrayList<Block>()
        var start = -1
        var i = 0
        while (i + 2 < text.length + 0 && i < text.length) {
            if (text[i] == '^' && i + 2 < text.length + 1 && i + 3 <= text.length) {
                val code = text.substring(i + 1, i + 3).uppercase()
                if (code == "XA") {
                    start = i
                    i += 3
                    continue
                }
                if (code == "XZ" && start != -1) {
                    out.add(Block(start, i + 3, true))
                    start = -1
                    i += 3
                    continue
                }
            }
            i++
        }
        if (start != -1) out.add(Block(start, text.length, false))
        return out
    }

    data class BlockAt(val start: Int, val end: Int, val closed: Boolean, val body: String)

    /**
     * Returns the block the caret is INSIDE; `null` when it is in none.
     *
     * `null` is deliberate: picking the nearest block would let the user
     * look at another label while believing they are editing it. Between two
     * blocks the honest answer is "I do not know which one".
     *
     * The upper bound is inclusive: a user who has just typed `^XZ` with the
     * caret right after it is still inside that label.
     */
    fun blockAt(text: String, offset: Int): BlockAt? {
        for (b in labelBlocks(text)) {
            if (offset >= b.start && offset <= b.end) {
                return BlockAt(b.start, b.end, b.closed, text.substring(b.start, b.end))
            }
        }
        return null
    }

    // ------------------------------------------------ finding -> editor --

    enum class Severity { ERROR, WARNING, INFO }

    /** A quick-fix edit: replace `[start, end)` with `newText`. */
    data class Edit(val start: Int, val end: Int, val newText: String, val title: String)

    /** Zero-based positions, as the editor wants them. */
    data class Finding(
        val line: Int,
        val col: Int,
        val endLine: Int,
        val endCol: Int,
        val message: String,
        val code: String,
        val url: String?,
        val severity: Severity,
        val fix: Edit?,
    )

    /**
     * Maps the server severity; an unknown value falls back to WARNING, not
     * ERROR: if the server adds a new class one day the plugin must neither
     * over-dramatise it nor hide it.
     */
    fun severity(value: String?): Severity = when (value?.lowercase()) {
        "error" -> Severity.ERROR
        "info" -> Severity.INFO
        else -> Severity.WARNING
    }

    private fun JsonObject.int(name: String, default: Int): Int {
        val e = get(name) ?: return default
        return if (e.isJsonPrimitive && e.asJsonPrimitive.isNumber) e.asInt else default
    }

    private fun JsonObject.str(name: String): String? {
        val e = get(name) ?: return null
        return if (e.isJsonPrimitive) e.asString else null
    }

    /**
     * Normalises a finding's `quickfix` into one edit; `null` when the
     * finding carries none.
     *
     * Two rules are enforced here rather than trusted: offsets are validated
     * against the text that was linted (an out-of-range edit would throw in
     * the editor and look like a fix that silently did nothing), and a
     * missing title is not invented (an unlabelled fix asks the user to
     * accept a change they cannot read).
     */
    fun quickfixEdit(finding: JsonObject, textLength: Int?): Edit? {
        val q = finding.get(Contract.FIX)
        if (q == null || !q.isJsonObject) return null
        val fix = q.asJsonObject
        val title = fix.str(Contract.FIX_TITLE) ?: return null
        if (title.isEmpty()) return null
        val start = fix.int(Contract.FIX_START, -1)
        val end = fix.int(Contract.FIX_END, -1)
        if (start < 0 || end < start) return null
        if (textLength != null && end > textLength) return null
        return Edit(start, end, fix.str(Contract.FIX_TEXT) ?: "", title)
    }

    /**
     * Turns a diagnostics response into the list the editor draws.
     *
     * The server reports 1-based line/column; the editor wants 0-based.
     * Passing the numbers through unchanged would paint every finding one
     * line down and one column right. When `end_line`/`end_col` are absent
     * the start is used: a zero-length range is still visible, whereas an
     * invented width would claim a precision that does not exist.
     *
     * The message falls back to the key and then the code: an empty marker
     * would be worse, the user could not even search for the rule.
     */
    fun prepareFindings(report: String, textLength: Int?): List<Finding> {
        val root: JsonElement = try {
            JsonParser.parseString(report)
        } catch (e: RuntimeException) {
            return emptyList()
        }
        if (!root.isJsonObject) return emptyList()
        val list = root.asJsonObject.get(Contract.FINDINGS)
        if (list == null || !list.isJsonArray) return emptyList()
        return list.asJsonArray.mapNotNull { e ->
            if (!e.isJsonObject) return@mapNotNull null
            val f = e.asJsonObject
            val line = f.int("line", 1)
            val col = f.int("col", 1)
            val code = f.str("code") ?: ""
            val url = f.str(Contract.RULE_URL)
            Finding(
                line = maxOf(0, line - 1),
                col = maxOf(0, col - 1),
                endLine = maxOf(0, f.int("end_line", line) - 1),
                endCol = maxOf(0, f.int("end_col", col) - 1),
                message = f.str(Contract.MESSAGE) ?: f.str(Contract.MESSAGE_KEY) ?: code,
                code = code,
                // Only http(s): the value comes from the network and becomes a link.
                url = if (url != null && Regex("^https?://").containsMatchIn(url)) url else null,
                severity = severity(f.str("severity")),
                fix = quickfixEdit(f, textLength),
            )
        }
    }
}
