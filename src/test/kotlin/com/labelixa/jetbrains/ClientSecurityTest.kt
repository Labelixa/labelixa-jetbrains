package com.labelixa.jetbrains

import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClientSecurityTest {
    private lateinit var server: HttpServer
    private lateinit var base: String

    /** Every request the local server saw: path and X-API-Key ("" when absent). */
    private val seen: MutableList<Pair<String, String>> = Collections.synchronizedList(ArrayList())

    @BeforeTest
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            seen.add(ex.requestURI.path to (ex.requestHeaders.getFirst("X-API-Key") ?: ""))
            ex.requestBody.readAllBytes()
            if (ex.requestURI.path.startsWith("/v1/diagnostics")) {
                // Points at the same server under another name: a followed
                // redirect would show up as a second request in `seen`.
                ex.responseHeaders.add("Location", "http://localhost:${server.address.port}/stolen")
                ex.sendResponseHeaders(307, -1)
            } else {
                val body = "{}".toByteArray()
                ex.sendResponseHeaders(200, body.size.toLong())
                ex.responseBody.use { it.write(body) }
            }
            ex.close()
        }
        server.start()
        base = "http://127.0.0.1:${server.address.port}"
    }

    @AfterTest
    fun stop() = server.stop(0)

    @Test
    fun `key policy trusts only Labelixa https hosts`() {
        assertEquals(Core.KeyPolicy.TRUSTED, Core.keyPolicy("https://api.labelixa.com"))
        assertEquals(Core.KeyPolicy.TRUSTED, Core.keyPolicy("https://staging.labelixa.com/"))
        assertEquals(Core.KeyPolicy.OPT_IN, Core.keyPolicy("https://api.labelixa.com:8443"))
        assertEquals(Core.KeyPolicy.OPT_IN, Core.keyPolicy("https://labels.example.internal"))
        assertEquals(Core.KeyPolicy.OPT_IN, Core.keyPolicy("http://localhost:8000"))
        assertEquals(Core.KeyPolicy.REFUSE, Core.keyPolicy("http://api.labelixa.com"))
        assertEquals(Core.KeyPolicy.REFUSE, Core.keyPolicy("http://10.0.0.5"))
        assertEquals(Core.KeyPolicy.REFUSE, Core.keyPolicy("https://api.labelixa.com@evil.test"))
        assertEquals(Core.KeyPolicy.REFUSE, Core.keyPolicy("ftp://api.labelixa.com"))
        assertEquals(Core.KeyPolicy.REFUSE, Core.keyPolicy("not a url"))
    }

    @Test
    fun `opt-in is bound to one origin`() {
        val origin = Core.keyOrigin("https://labels.example.internal/api")
        assertEquals("https://labels.example.internal", origin)
        assertTrue(Core.sendKey("https://labels.example.internal", origin))
        assertFalse(Core.sendKey("https://other.example.internal", origin))
        assertFalse(Core.sendKey("https://labels.example.internal", ""))
        assertTrue(Core.sendKey("https://api.labelixa.com", ""))
        assertFalse(Core.sendKey("http://10.0.0.5", "http://10.0.0.5"))
    }

    @Test
    fun `plain http to another machine is refused before anything is sent`() {
        val c = LabelixaClient("lbx_test", "http://10.0.0.5", "t", "http://10.0.0.5")
        assertFalse(c.sendsKey)
        val e = assertFailsWith<IOException> { c.diagnostics("^XA^XZ", 8, 4.0, 6.0) }
        assertTrue(e.message!!.contains("https"))
        assertNull(Core.baseUrlProblem("https://api.labelixa.com"))
    }

    @Test
    fun `a custom address gets no key without the opt-in`() {
        val c = LabelixaClient("lbx_test", base, "t")
        assertFalse(c.sendsKey)
        c.renderPng("^XA^XZ", 8, 4.0, 6.0)
        assertEquals("", seen.single().second)
    }

    @Test
    fun `redirects are not followed and the key stays put`() {
        val c = LabelixaClient("lbx_test", base, "t", Core.keyOrigin(base))
        assertTrue(c.sendsKey)
        val e = assertFailsWith<LabelixaException> { c.diagnostics("^XA^XZ", 8, 4.0, 6.0) }
        assertEquals(307, e.status)
        assertEquals(1, seen.size)
        assertEquals("lbx_test", seen[0].second)
        assertFalse(seen.any { it.first == "/stolen" })
    }

    @Test
    fun `a redirect-following HttpClient cannot carry the key`() {
        val following = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
        assertFailsWith<IllegalArgumentException> {
            LabelixaClient("lbx_test", Core.DEFAULT_BASE_URL, "t", "", following)
        }
        // Without a key there is nothing to leak.
        LabelixaClient(null, Core.DEFAULT_BASE_URL, "t", "", following)
    }
}
