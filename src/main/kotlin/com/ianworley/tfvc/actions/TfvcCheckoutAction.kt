package com.ianworley.tfvc.actions

import com.ianworley.tfvc.vcs.TfvcActionSupport
import com.ianworley.tfvc.vcs.TfvcEditFileProvider
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

class TfvcCheckoutAction : AnAction(), DumbAware {
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

        TfvcActionSupport.runBackground(project, "Checking out TFVC files") {
            TfvcEditFileProvider(project).checkoutFiles(files)
        }
    }
}
