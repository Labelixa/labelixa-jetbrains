package com.labelixa.jetbrains

import com.intellij.icons.AllIcons
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object ZplLanguage : Language("ZPL")

/**
 * `.zpl` files. The parser definition (ZplParserDefinition) is deliberately
 * flat: one leaf per lexer token, no grammar. It exists so the file carries
 * the ZPL language (the annotator and the validate action key off it); the
 * API does the real parsing, a second grammar in the editor would be a
 * second source of truth that drifts.
 */
// A Kotlin `object` already exposes a static `INSTANCE` field, which is what
// plugin.xml's `fieldName="INSTANCE"` refers to.
object ZplFileType : LanguageFileType(ZplLanguage) {
    override fun getName(): String = "ZPL"
    override fun getDescription(): String = "Zebra Programming Language label"
    override fun getDefaultExtension(): String = "zpl"
    override fun getIcon(): Icon = AllIcons.FileTypes.Text
}
