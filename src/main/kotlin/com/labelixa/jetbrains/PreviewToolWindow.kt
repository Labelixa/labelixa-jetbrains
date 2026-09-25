package com.labelixa.jetbrains

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.swing.ImageIcon
import javax.swing.JPanel
import javax.swing.SwingConstants

const val PREVIEW_TOOL_WINDOW_ID = "Labelixa Preview"

/**
 * The preview panel: the rendered PNG at its real pixel size on white
 * paper, so the user sees the printer's actual dots, and a one-line status
 * (which label, how many dots) above it.
 */
class PreviewPanel : JPanel(BorderLayout()) {
    private val status = JBLabel("Run \"Labelixa: Preview Label Under Cursor\" in a ZPL file.")
    private val image = JBLabel("", SwingConstants.CENTER)

    init {
        status.border = javax.swing.BorderFactory.createEmptyBorder(6, 8, 6, 8)
        image.background = Color.WHITE
        image.isOpaque = false
        add(status, BorderLayout.NORTH)
        add(JBScrollPane(image), BorderLayout.CENTER)
    }

    fun show(png: ByteArray, title: String) {
        val img: BufferedImage? = ImageIO.read(ByteArrayInputStream(png))
        if (img == null) {
            status.text = "$title: the API did not return an image."
            image.icon = null
            return
        }
        image.icon = ImageIcon(img)
        image.isOpaque = true
        status.text = "$title — ${img.width} × ${img.height} dots"
    }

    fun message(text: String) {
        status.text = text
    }
}

class PreviewToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = PreviewPanel()
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    companion object {
        /** The panel of the project's preview tool window, creating the content if needed. */
        fun panel(project: Project): Pair<ToolWindow, PreviewPanel>? {
            val tw = ToolWindowManager.getInstance(project).getToolWindow(PREVIEW_TOOL_WINDOW_ID) ?: return null
            val existing = tw.contentManager.contents.firstOrNull()?.component as? PreviewPanel
            if (existing != null) return tw to existing
            val panel = PreviewPanel()
            tw.contentManager.addContent(ContentFactory.getInstance().createContent(panel, "", false))
            return tw to panel
        }
    }
}
