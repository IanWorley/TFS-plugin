package com.ianworley.tfvc.actions

import com.ianworley.tfvc.tf.TfCommandRunner
import com.ianworley.tfvc.tf.TfvcCommandBuilder
import com.ianworley.tfvc.tf.TfvcNotifications
import com.ianworley.tfvc.ui.TfvcShelveDialog
import com.ianworley.tfvc.vcs.TfvcActionSupport
import com.ianworley.tfvc.vcs.TfvcStatusCache
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

class TfvcShelveAction : AnAction(), DumbAware {
    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val files = TfvcActionSupport.tfvcFiles(project, TfvcActionSupport.selectedFiles(event))
        val root = TfvcActionSupport.firstRelevantRoot(project, files) ?: run {
            TfvcNotifications.warn(project, "TFVC unavailable", "No TFVC root is mapped for this project.")
            return
        }

        val dialog = TfvcShelveDialog(project)
        if (!dialog.showAndGet()) {
            return
        }
        val request = dialog.request()
        val paths = files.map { it.toNioPath() }

        TfvcActionSupport.runBackground(project, "Creating TFVC shelveset") {
            val result = TfCommandRunner.getInstance(project).run(
                args = TfvcCommandBuilder.shelve(
                    name = request.name,
                    paths = paths,
                    comment = request.comment,
                    replaceExisting = request.replaceExisting,
                    moveChanges = request.removePendingChangesAfterShelve,
                ),
                workingDirectory = root,
            )
            if (!result.isSuccessfulLike) {
                TfvcNotifications.error(
                    project,
                    "TFVC shelve failed",
                    "Fix tf.exe access or workspace state externally, then retry.\n${result.stderr.ifBlank { "tf shelve failed." }}",
                )
            } else {
                TfvcStatusCache.getInstance(project).requestRefresh(root)
            }
        }
    }
}
