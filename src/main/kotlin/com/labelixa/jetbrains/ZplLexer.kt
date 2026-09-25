package com.labelixa.jetbrains

import com.intellij.lexer.LexerBase
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType

object ZplTokens {
    @JvmField val COMMAND = IElementType("ZPL_COMMAND", ZplLanguage)
    @JvmField val COMMENT = IElementType("ZPL_COMMENT", ZplLanguage)
    @JvmField val TEXT = IElementType("ZPL_TEXT", ZplLanguage)
}

/**
 * Three token kinds are enough for highlighting: a command code (`^XX` or
 * `~XX`, the caret or tilde plus two characters), a comment (`^FX` up to the
 * next command) and everything else. Parameters and field data are left
 * as plain text: colouring them would need the command dictionary, which
 * is the API's, not the editor's.
 */
class ZplLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var end = 0
    private var tokenStart = 0
    private var tokenEnd = 0
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.end = endOffset
        tokenStart = startOffset
        tokenEnd = startOffset
        advance()
    }

    override fun getState(): Int = 0
    override fun getTokenType(): IElementType? = tokenType
    override fun getTokenStart(): Int = tokenStart
    override fun getTokenEnd(): Int = tokenEnd
    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = end

    private fun isPrefix(i: Int): Boolean =
        i < end && (buffer[i] == '^' || buffer[i] == '~')

    private fun isCommandAt(i: Int): Boolean =
        isPrefix(i) && i + 2 < end + 0 && i + 3 <= end &&
            buffer[i + 1].isLetterOrDigit() && buffer[i + 2].isLetterOrDigit()

    override fun advance() {
        tokenStart = tokenEnd
        if (tokenStart >= end) {
            tokenType = null
            return
        }
        var i = tokenStart
        if (isCommandAt(i)) {
            val code = buffer.subSequence(i + 1, i + 3).toString().uppercase()
            if (buffer[i] == '^' && code == "FX") {
                i += 3
                while (i < end && !isPrefix(i)) i++
                tokenEnd = i
                tokenType = ZplTokens.COMMENT
            } else {
                tokenEnd = i + 3
                tokenType = ZplTokens.COMMAND
            }
            return
        }
        i++
        while (i < end && !isCommandAt(i)) i++
        tokenEnd = i
        tokenType = ZplTokens.TEXT
    }
}

class ZplSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer() = ZplLexer()

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> = when (tokenType) {
        ZplTokens.COMMAND -> arrayOf(COMMAND)
        ZplTokens.COMMENT -> arrayOf(COMMENT)
        else -> emptyArray()
    }

    companion object {
        val COMMAND: TextAttributesKey =
            TextAttributesKey.createTextAttributesKey("ZPL_COMMAND", DefaultLanguageHighlighterColors.KEYWORD)
        val COMMENT: TextAttributesKey =
            TextAttributesKey.createTextAttributesKey("ZPL_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
    }
}

class ZplSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter =
        ZplSyntaxHighlighter()
}
