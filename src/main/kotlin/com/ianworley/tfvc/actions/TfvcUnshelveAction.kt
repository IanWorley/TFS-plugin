package com.ianworley.tfvc.actions

import com.ianworley.tfvc.parsing.TfShelvesetsParser
import com.ianworley.tfvc.tf.TfCommandRunner
import com.ianworley.tfvc.tf.TfvcCommandBuilder
import com.ianworley.tfvc.tf.TfvcNotifications
import com.ianworley.tfvc.ui.TfvcUnshelveDialog
import com.ianworley.tfvc.vcs.TfvcActionSupport
import com.ianworley.tfvc.vcs.TfvcStatusCache
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware

class TfvcUnshelveAction : AnAction(), DumbAware {
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

        val currentUser = System.getProperty("user.name").orEmpty().ifBlank { "*" }
        val dialog = TfvcUnshelveDialog(project, currentUser) { owner ->
            val result = TfCommandRunner.getInstance(project).run(
                args = TfvcCommandBuilder.shelvesets(owner),
                workingDirectory = root,
            )
            if (!result.isSuccessfulLike) {
                TfvcNotifications.error(
                    project,
                    "TFVC shelveset lookup failed",
                    "Fix tf.exe access or workspace state externally, then retry.\n${result.stderr.ifBlank { "tf shelvesets failed." }}",
                )
                emptyList()
            } else {
                TfShelvesetsParser.parse(result.stdout)
            }
        }
        if (!dialog.showAndGet()) {
            return
        }
        val request = dialog.request()

        TfvcActionSupport.runBackground(project, "Unshelving TFVC shelveset") {
            val result = TfCommandRunner.getInstance(project).run(
                args = TfvcCommandBuilder.unshelve(
                    name = request.shelveset.name,
                    owner = request.shelveset.owner,
                    removeAfterUnshelve = request.removeAfterUnshelve,
                ),
                workingDirectory = root,
            )
            if (!result.isSuccessfulLike) {
                TfvcNotifications.error(
                    project,
                    "TFVC unshelve failed",
                    "Fix tf.exe access or workspace state externally, then retry.\n${result.stderr.ifBlank { "tf unshelve failed." }}",
                )
                return@runBackground
            }

            val fileDocumentManager = FileDocumentManager.getInstance()
            FileEditorManager.getInstance(project).openFiles
                .filter { it.toNioPath().normalize().startsWith(root.normalize()) }
                .forEach { file ->
                    file.refresh(false, false)
                    fileDocumentManager.getDocument(file)?.let(fileDocumentManager::reloadFromDisk)
                }

            TfvcStatusCache.getInstance(project).requestRefresh(root)
        }
    }
}
