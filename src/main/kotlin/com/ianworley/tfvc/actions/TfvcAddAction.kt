package com.ianworley.tfvc.actions

import com.ianworley.tfvc.vcs.TfvcActionSupport
import com.ianworley.tfvc.vcs.TfvcVfsListener
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware

class TfvcAddAction : AnAction(), DumbAware {
    override fun update(event: AnActionEvent) {
        val project = event.project
        val visibleFiles = if (project == null) emptyList() else TfvcActionSupport.tfvcFiles(project, TfvcActionSupport.selectedFiles(event))
        event.presentation.isEnabledAndVisible = project != null && visibleFiles.isNotEmpty()
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val files = TfvcActionSupport.tfvcFiles(project, TfvcActionSupport.selectedFiles(event))
        if (files.isEmpty()) {
            return
        }

        TfvcActionSupport.runBackground(project, "Adding files to TFVC") {
            TfvcVfsListener(project).addFiles(files)
        }
    }
}
