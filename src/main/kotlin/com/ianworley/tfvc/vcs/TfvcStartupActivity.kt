package com.ianworley.tfvc.vcs

import com.ianworley.tfvc.settings.TfvcSettingsState
import com.ianworley.tfvc.tf.TfCommandRunner
import com.ianworley.tfvc.tf.TfvcCommandBuilder
import com.ianworley.tfvc.tf.TfvcNotifications
import com.ianworley.tfvc.tf.TfvcWorkspaceService
import com.ianworley.tfvc.tf.WorkspaceType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vfs.VirtualFileManager
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

        ApplicationManager.getApplication().executeOnPooledThread {
            initializeTrackedRoots(project)
        }
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

    private fun initializeTrackedRoots(project: Project) {
        if (project.isDisposed) {
            return
        }

        val workspaceRoots = detectWorkspaceRoots(project)
        if (workspaceRoots.isEmpty()) {
            return
        }

        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                return@invokeLater
            }

            ensureTfvcMappings(project, workspaceRoots)

            val statusCache = TfvcStatusCache.getInstance(project)
            workspaceRoots.forEach(statusCache::requestRefresh)
        }
    }

    private fun detectWorkspaceRoots(project: Project): List<Path> {
        val candidates = linkedSetOf<Path>()
        project.basePath
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
            ?.let(candidates::add)
        ProjectRootManager.getInstance(project).contentRoots
            .mapNotNullTo(candidates) { root ->
                if (!root.isInLocalFileSystem) {
                    return@mapNotNullTo null
                }

                runCatching { root.toNioPath() }.getOrNull()
            }
        TfvcActionSupport.mappedTfvcRoots(project).mapTo(candidates, Path::normalize)

        val workspaceService = TfvcWorkspaceService.getInstance(project)
        return candidates
            .mapNotNull(workspaceService::findWorkspaceFor)
            .map { it.localRoot.normalize() }
            .distinct()
    }

    private fun ensureTfvcMappings(project: Project, roots: Collection<Path>) {
        val vcsManager = ProjectLevelVcsManager.getInstance(project)
        val existingMappings = vcsManager.directoryMappings.toMutableList()
        val existingMappingDirectories = existingMappings
            .mapNotNull { mapping -> normalizeMappingDirectory(mapping.directory) }
            .toMutableSet()

        var changed = false
        roots.forEach { root ->
            val normalizedRoot = root.toAbsolutePath().normalize().toString()
            if (normalizedRoot in existingMappingDirectories) {
                return@forEach
            }

            existingMappings += VcsDirectoryMapping(normalizedRoot, TfvcVcs.NAME)
            existingMappingDirectories += normalizedRoot
            changed = true
        }

        if (changed) {
            vcsManager.setDirectoryMappings(existingMappings)
        }
    }

    private fun normalizeMappingDirectory(directory: String): String? =
        directory.takeIf(String::isNotBlank)?.let { Path.of(it).toAbsolutePath().normalize().toString() }
}
