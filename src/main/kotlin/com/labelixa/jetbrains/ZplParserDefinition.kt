package com.labelixa.jetbrains

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

/**
 * The flattest possible parser: one file node, one leaf per lexer token.
 *
 * Without a parser definition the platform builds a plain-text PSI for
 * `.zpl` files, so the file's language is PlainText rather than ZPL. Every
 * extension registered for `language="ZPL"` (the external annotator) and
 * every action that checks the PSI language (Validate ZPL) is then never
 * invoked, even though highlighting works. This definition gives the file
 * the ZPL language without introducing a grammar: the API still does the
 * real parsing, the tree here carries no structure beyond tokens.
 */
class ZplParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = ZplLexer()

    override fun createParser(project: Project?): PsiParser = PsiParser { root, builder ->
        val mark = builder.mark()
        while (!builder.eof()) builder.advanceLexer()
        mark.done(root)
        builder.treeBuilt
    }

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getCommentTokens(): TokenSet = COMMENTS

    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

    override fun createElement(node: ASTNode): PsiElement = LeafPsiElement(node.elementType, node.text)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = ZplFile(viewProvider)

    companion object {
        @JvmField val FILE = IFileElementType(ZplLanguage)
        @JvmField val COMMENTS: TokenSet = TokenSet.create(ZplTokens.COMMENT)
    }
}

class ZplFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, ZplLanguage) {
    override fun getFileType(): FileType = ZplFileType
    override fun toString(): String = "ZPL file"
}
