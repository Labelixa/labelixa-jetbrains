package com.labelixa.jetbrains

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware

/**
 * Renders the ^XA…^XZ block under the caret and shows it in the preview
 * tool window. Rendering consumes label quota, so it only ever happens on
 * this explicit request.
 */
class PreviewAction : AnAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.getData(CommonDataKeys.EDITOR) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val text = editor.document.text
        val block = Core.blockAt(text, editor.caretModel.offset)
        if (block == null) {
            Plugin.notify(project, "The cursor is not inside a ^XA … ^XZ block. Nothing was rendered.")
            return
        }
        if (!block.closed) {
            Plugin.notify(project, "^XZ is missing: the label under the cursor is not closed. Nothing was rendered.")
            return
        }
        val s = LabelixaSettings.get()
        val line = editor.document.getLineNumber(block.start) + 1
        val title = "Label at line $line"
        val client = Plugin.client()
        val dpmm = s.dpmm
        val w = s.widthIn
        val h = s.heightIn
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Labelixa: rendering label", true) {
            override fun run(indicator: ProgressIndicator) {
                val png = try {
                    client.renderPng(block.body, dpmm, w, h)
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Plugin.notify(project, Plugin.describe(ex), NotificationType.ERROR)
                    }
                    return
                }
                ApplicationManager.getApplication().invokeLater {
                    val (tw, panel) = PreviewToolWindowFactory.panel(project) ?: return@invokeLater
                    panel.show(png, "$title ($dpmm dots/mm, ${Core.g(w)}×${Core.g(h)} in)")
                    tw.show()
                }
            }
        })
    }
}

/**
 * One explicit validation pass: marks the file so the external annotator
 * fetches findings on its next run, then asks the daemon to run.
 */
class ValidateAction : AnAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.PSI_FILE)
        e.presentation.isEnabledAndVisible = file != null && file.language == ZplLanguage
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return
        val vf = file.virtualFile ?: return
        LintState.get().force(vf.path)
        DaemonCodeAnalyzer.getInstance(project).restart(file)
    }
}
