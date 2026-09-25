package com.labelixa.jetbrains

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project

/** Small helpers shared by the actions and the annotator. */
object Plugin {
    const val ID = "com.labelixa.zpl"

    val version: String
        get() = PluginManagerCore.getPlugin(PluginId.getId(ID))?.version ?: "0.0.0"

    fun client(): LabelixaClient {
        val s = LabelixaSettings.get()
        return LabelixaClient(s.apiKey, s.baseUrl, version)
    }

    fun notify(project: Project?, text: String, type: NotificationType = NotificationType.WARNING) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Labelixa")
            .createNotification("Labelixa", text, type)
            .notify(project)
    }

    /** One sentence for the user; quota and rate limit are named as such. */
    fun describe(e: Exception): String = when (e) {
        is LabelixaException ->
            if (e.quota) "Quota or rate limit reached (HTTP ${e.status}); retry after ${e.retryAfter} s."
            else "The API answered HTTP ${e.status}: ${e.serverMessage.take(200)}"
        else -> "Could not reach the Labelixa API: ${e.message ?: e.javaClass.simpleName}"
    }
}
