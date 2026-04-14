package com.ianworley.tfvc.tf

import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager

object TfvcNotifications {
    private const val GROUP_ID = "TFVC"
    private val logger = Logger.getInstance(TfvcNotifications::class.java)

    fun info(project: Project, title: String, content: String) {
        notify(project, title, content, NotificationType.INFORMATION)
    }

    fun warn(project: Project, title: String, content: String) {
        notify(project, title, content, NotificationType.WARNING)
    }

    fun error(project: Project, title: String, content: String) {
        notify(project, title, content, NotificationType.ERROR)
    }

    fun logToConsole(project: Project, message: String) {
        logger.info(message)
        ProjectLevelVcsManager.getInstance(project).addMessageToConsoleWindow("[TFVC] $message")
    }

    private fun notify(project: Project, title: String, content: String, type: NotificationType) {
        val notification = com.intellij.notification.Notification(GROUP_ID, title, content, type)
        Notifications.Bus.notify(notification, project)
        logToConsole(project, "$title: ${content.replace('\n', ' ')}")
    }
}
