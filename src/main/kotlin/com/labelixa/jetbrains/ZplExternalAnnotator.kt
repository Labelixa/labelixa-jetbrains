package com.labelixa.jetbrains

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import java.util.concurrent.ConcurrentHashMap

/**
 * Which files asked for a one-shot validation, and the last findings per
 * file. The cache keeps findings visible after an explicit validation until
 * the next one; without it the daemon's next pass would wipe them the
 * moment the user types.
 */
@Service(Service.Level.APP)
class LintState {
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val cache = ConcurrentHashMap<String, ZplExternalAnnotator.Result>()

    fun force(path: String) { pending.add(path) }
    fun take(path: String): Boolean = pending.remove(path)
    fun cached(path: String): ZplExternalAnnotator.Result? = cache[path]
    fun remember(path: String, r: ZplExternalAnnotator.Result) { cache[path] = r }

    companion object {
        fun get(): LintState = service()
    }
}

class ZplExternalAnnotator : ExternalAnnotator<ZplExternalAnnotator.Info, ZplExternalAnnotator.Result>() {
    /** `text == null` means "re-apply the cached result, do not call the API". */
    class Info(val path: String, val text: String?, val forced: Boolean, val cached: Result?)
    class Result(val findings: List<Core.Finding>, val error: String?, val notify: Boolean)

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): Info? {
        val vf = file.virtualFile ?: return null
        val path = vf.path
        val state = LintState.get()
        val forced = state.take(path)
        if (forced || LabelixaSettings.get().lintWhileTyping) {
            return Info(path, editor.document.text, forced, null)
        }
        val cached = state.cached(path) ?: return null
        return Info(path, null, false, cached)
    }

    override fun doAnnotate(info: Info?): Result? {
        if (info == null) return null
        if (info.text == null) return info.cached
        val s = LabelixaSettings.get()
        val result = try {
            val report = Plugin.client().diagnostics(info.text, s.dpmm, s.widthIn, s.heightIn)
            Result(Core.prepareFindings(report, info.text.length), null, info.forced)
        } catch (ex: Exception) {
            Result(emptyList(), Plugin.describe(ex), info.forced)
        }
        LintState.get().remember(info.path, Result(result.findings, result.error, false))
        return result
    }

    override fun apply(file: PsiFile, result: Result?, holder: AnnotationHolder) {
        if (result == null) return
        val project = file.project
        if (result.error != null) {
            if (result.notify) {
                ApplicationManager.getApplication().invokeLater {
                    Plugin.notify(project, result.error, NotificationType.ERROR)
                }
            }
            return
        }
        if (result.notify) {
            val n = result.findings.size
            ApplicationManager.getApplication().invokeLater {
                Plugin.notify(project, if (n == 0) "No findings." else "$n finding(s); see the editor markers.",
                    NotificationType.INFORMATION)
            }
        }
        val doc = file.viewProvider.document ?: return
        val length = doc.textLength
        for (f in result.findings) {
            val range = range(doc.lineCount, { doc.getLineStartOffset(it) }, { doc.getLineEndOffset(it) }, length, f)
                ?: continue
            val severity = when (f.severity) {
                Core.Severity.ERROR -> HighlightSeverity.ERROR
                Core.Severity.WARNING -> HighlightSeverity.WARNING
                Core.Severity.INFO -> HighlightSeverity.WEAK_WARNING
            }
            val label = if (f.code.isNotEmpty()) "${f.message} [${f.code}]" else f.message
            var builder = holder.newAnnotation(severity, label).range(range)
            if (f.url != null) {
                builder = builder.tooltip("<html>${escape(f.message)} <a href=\"${escape(f.url)}\">${escape(f.code)}</a></html>")
            }
            val fix = f.fix
            if (fix != null) builder = builder.withFix(ReplaceFix(fix))
            builder.create()
        }
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    companion object {
        /**
         * Line/column (0-based) to a document range, clamped to the document.
         * A finding past the end of the document (the text changed since the
         * request) is dropped rather than drawn in the wrong place.
         */
        fun range(
            lineCount: Int,
            lineStart: (Int) -> Int,
            lineEnd: (Int) -> Int,
            length: Int,
            f: Core.Finding,
        ): TextRange? {
            if (lineCount == 0 || f.line >= lineCount) return null
            val start = minOf(lineStart(f.line) + f.col, lineEnd(f.line))
            val endLine = minOf(f.endLine, lineCount - 1)
            var end = minOf(lineStart(endLine) + f.endCol, lineEnd(endLine))
            if (end <= start) end = minOf(start + 1, length)
            if (start > length) return null
            return TextRange(start, maxOf(start, end))
        }
    }
}

/** Replaces `[start, end)` with the server's text; the range is re-checked at apply time. */
class ReplaceFix(private val edit: Core.Edit) : IntentionAction {
    override fun getText(): String = edit.title
    override fun getFamilyName(): String = "Labelixa"
    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        val doc = file?.viewProvider?.document ?: return false
        return edit.end <= doc.textLength
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val doc = file?.viewProvider?.document ?: return
        if (edit.end > doc.textLength) return
        WriteCommandAction.runWriteCommandAction(project) {
            doc.replaceString(edit.start, edit.end, edit.newText)
        }
    }
}
