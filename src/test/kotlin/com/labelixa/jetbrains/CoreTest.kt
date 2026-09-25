package com.labelixa.jetbrains

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreTest {
    private val two = "^XA^FO10,10^FDone^FS^XZ\n\n^XA^FO10,10^FDtwo^FS^XZ\n"

    @Test
    fun `blocks carry offsets and closed flag`() {
        val blocks = Core.labelBlocks(two)
        assertEquals(2, blocks.size)
        assertEquals(0, blocks[0].start)
        assertEquals(two.indexOf("^XZ") + 3, blocks[0].end)
        assertTrue(blocks.all { it.closed })
    }

    @Test
    fun `a second XA restarts the block like the engine`() {
        val text = "^XA^FDfirst^FS^XA^FDsecond^FS^XZ"
        val blocks = Core.labelBlocks(text)
        assertEquals(1, blocks.size)
        assertEquals(text.indexOf("^XA", 1), blocks[0].start)
    }

    @Test
    fun `unterminated last block is reported open, not dropped`() {
        val blocks = Core.labelBlocks("^XA^FDtyping")
        assertEquals(1, blocks.size)
        assertEquals(false, blocks[0].closed)
    }

    @Test
    fun `blockAt is null between labels and inclusive at XZ`() {
        assertNull(Core.blockAt(two, two.indexOf("\n\n") + 1))
        val b = Core.blockAt(two, two.indexOf("^XZ") + 3)
        assertNotNull(b)
        assertEquals("^XA^FO10,10^FDone^FS^XZ", b.body)
        assertTrue(Core.blockAt(two, two.lastIndexOf("^FD") + 1)!!.body.contains("two"))
    }

    @Test
    fun `render path formats sizes like the SDKs`() {
        assertEquals("/v1/printers/8dpmm/labels/4x6/0", Core.renderPath(8, 4.0, 6.0, 0))
        assertEquals("/v1/printers/12dpmm/labels/2.25x1.25/1", Core.renderPath(12, 2.25, 1.25, 1))
    }

    @Test
    fun `findings become zero based and keep only http urls`() {
        val report = """{"diagnostics":[
            {"line":3,"col":5,"end_line":3,"end_col":9,"code":"ZPL001","mesaj":"Unknown command","severity":"error","url":"https://labelixa.com/zpl/rules/ZPL001"},
            {"line":1,"col":1,"code":"ZPL002","message_key":"x","severity":"odd","url":"javascript:alert(1)"}
        ]}"""
        val f = Core.prepareFindings(report, 100)
        assertEquals(2, f.size)
        assertEquals(2, f[0].line)
        assertEquals(4, f[0].col)
        assertEquals(8, f[0].endCol)
        assertEquals(Core.Severity.ERROR, f[0].severity)
        assertEquals("https://labelixa.com/zpl/rules/ZPL001", f[0].url)
        assertEquals("x", f[1].message)
        assertEquals(Core.Severity.WARNING, f[1].severity)
        assertNull(f[1].url)
        assertEquals(0, f[1].endLine)
        assertEquals(0, f[1].endCol)
    }

    @Test
    fun `quick fix needs a title and a range inside the text`() {
        val ok = """{"diagnostics":[{"line":1,"col":1,"code":"A","mesaj":"m",
            "quickfix":{"baslik":"Add ^FS","offset":4,"end_offset":4,"yeni":"^FS"}}]}"""
        val fix = Core.prepareFindings(ok, 10)[0].fix
        assertNotNull(fix)
        assertEquals("Add ^FS", fix.title)
        assertEquals("^FS", fix.newText)
        val outOfRange = ok.replace("\"offset\":4,\"end_offset\":4", "\"offset\":4,\"end_offset\":40")
        assertNull(Core.prepareFindings(outOfRange, 10)[0].fix)
        val untitled = ok.replace("\"baslik\":\"Add ^FS\",", "")
        assertNull(Core.prepareFindings(untitled, 10)[0].fix)
    }

    @Test
    fun `malformed report yields no findings instead of throwing`() {
        assertEquals(emptyList(), Core.prepareFindings("not json", 5))
        assertEquals(emptyList(), Core.prepareFindings("{\"diagnostics\":7}", 5))
    }

    @Test
    fun `editor range is clamped and stale positions are dropped`() {
        val text = "^XA\n^FO10,10\n^XZ"
        val starts = listOf(0, 4, 13)
        val ends = listOf(3, 12, 16)
        fun f(line: Int, col: Int, endLine: Int = line, endCol: Int = col) =
            Core.Finding(line, col, endLine, endCol, "m", "C", null, Core.Severity.WARNING, null)
        val r = ZplExternalAnnotator.range(3, { starts[it] }, { ends[it] }, text.length, f(1, 0, 1, 4))
        assertNotNull(r)
        assertEquals(4, r.startOffset)
        assertEquals(8, r.endOffset)
        // zero width becomes one character
        val z = ZplExternalAnnotator.range(3, { starts[it] }, { ends[it] }, text.length, f(0, 0))
        assertEquals(0 to 1, z!!.startOffset to z.endOffset)
        // past the end of the document: not drawn
        assertNull(ZplExternalAnnotator.range(3, { starts[it] }, { ends[it] }, text.length, f(7, 0)))
    }
}
