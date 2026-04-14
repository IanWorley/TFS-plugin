package com.ianworley.tfvc.vcs

import com.ianworley.tfvc.settings.TfvcSettingsState
import com.ianworley.tfvc.tf.TfCommandRunner
import com.ianworley.tfvc.tf.TfvcCommandBuilder
import com.ianworley.tfvc.tf.TfvcNotifications
import com.ianworley.tfvc.tf.TfvcWorkspaceService
import com.ianworley.tfvc.tf.WorkspaceType
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.messages.MessageBusConnection
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

class TfvcStartupActivity : StartupActivity.DumbAware {
    override fun runActivity(project: Project) {
        val connection = project.messageBus.connect()
        connection.subscribe(VirtualFileManager.VFS_CHANGES, TfvcVfsListener(project))

        val inFlight = ConcurrentHashMap.newKeySet<Path>()
        val multicaster = com.intellij.openapi.editor.EditorFactory.getInstance().eventMulticaster
        multicaster.addDocumentListener(
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    if (!TfvcSettingsState.getInstance(project).autoCheckoutOnEdit || project.isDisposed) {
                        return
                    }

                    val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
                    if (file.isDirectory || !file.isInLocalFileSystem) {
                        return
                    }

                    val path = file.toNioPath()
                    if (!inFlight.add(path)) {
                        return
                    }

                    com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
                        try {
                            maybeCheckoutOnEdit(project, path)
                        } finally {
                            inFlight.remove(path)
                        }
                    }
                }
            },
            project,
        )
    }

    private fun maybeCheckoutOnEdit(project: Project, path: Path) {
        val workspaceService = TfvcWorkspaceService.getInstance(project)
        val workspace = workspaceService.findWorkspaceFor(path) ?: return
        if (workspace.workspaceType != WorkspaceType.LOCAL) {
            return
        }

        val statusCache = TfvcStatusCache.getInstance(project)
        if (statusCache.hasPendingChange(path)) {
            return
        }

        val result = TfCommandRunner.getInstance(project).run(
            args = TfvcCommandBuilder.checkout(listOf(path)),
            workingDirectory = workspace.localRoot,
        )
        if (!result.isSuccessfulLike) {
            TfvcNotifications.warn(
                project,
                "Automatic TFVC checkout failed",
                "The file was edited locally, but tf checkout did not succeed.\n${result.stderr.ifBlank { "Fix tf.exe access or workspace state externally, then retry." }}",
            )
            return
        }

        statusCache.requestRefresh(workspace.localRoot)
    }
}
