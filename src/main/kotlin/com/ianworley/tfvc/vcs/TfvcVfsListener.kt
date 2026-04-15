package com.ianworley.tfvc.vcs

import com.ianworley.tfvc.tf.TfvcNotifications
import com.ianworley.tfvc.tf.TfvcWorkspaceService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

class TfvcVfsListener(
    private val project: Project,
) : BulkFileListener {
    private val pendingChangesSupport = TfvcPendingChangesSupport(project)

    override fun after(events: List<VFileEvent>) {
        val candidates = events.asSequence()
            .filterIsInstance<VFileCreateEvent>()
            .mapNotNull { LocalFileSystem.getInstance().findFileByPath(it.path) }
            .filter { file ->
                !project.isDisposed &&
                    !ChangeListManager.getInstance(project).isIgnoredFile(file) &&
                    TfvcWorkspaceService.getInstance(project).findWorkspaceFor(file.toNioPath()) != null
            }
            .toList()

        if (candidates.isEmpty()) {
            return
        }

        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                return@invokeLater
            }

            val message = if (candidates.size == 1) {
                "Add ${candidates.single().name} to TFVC?"
            } else {
                "Add ${candidates.size} new items to TFVC?"
            }
            val answer = Messages.showYesNoDialog(project, message, "TFVC Add", null)
            if (answer != Messages.YES) {
                return@invokeLater
            }

            TfvcActionSupport.runBackground(project, "Adding files to TFVC") {
                addFiles(candidates)
            }
        }
    }

    internal fun addFiles(files: Collection<VirtualFile>) {
        pendingChangesSupport.add(files).forEach { exception ->
            TfvcNotifications.error(
                project,
                "TFVC add failed",
                "Fix tf.exe access or workspace state externally, then retry.\n${exception.message}",
            )
        }
    }
}
