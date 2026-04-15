package com.ianworley.tfvc.vcs

import com.ianworley.tfvc.parsing.TfStatusParser
import com.ianworley.tfvc.settings.TfvcSettingsState
import com.ianworley.tfvc.tf.PendingChange
import com.ianworley.tfvc.tf.TfCommandRunner
import com.ianworley.tfvc.tf.TfvcCommandBuilder
import com.ianworley.tfvc.tf.TfvcNotifications
import com.ianworley.tfvc.tf.TfvcPathMapper
import com.ianworley.tfvc.tf.TfvcWorkspaceService
import com.ianworley.tfvc.tf.WorkspaceType
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

class TfvcStatusCache(
    private val project: Project,
) {
    private data class CachedStatus(
        val loadedAtMs: Long,
        val changes: List<PendingChange>,
    )

    private val cache = ConcurrentHashMap<Path, CachedStatus>()
    private val refreshQueue = MergingUpdateQueue("tfvc.status.refresh", 400, true, null, project)

    fun statusForRoot(root: Path, forceRefresh: Boolean = false): List<PendingChange> {
        val normalizedRoot = root.normalize()
        val currentTime = System.currentTimeMillis()
        val cached = cache[normalizedRoot]
        if (!forceRefresh && cached != null && currentTime - cached.loadedAtMs < 2_000L) {
            return cached.changes
        }

        val workspaceService = TfvcWorkspaceService.getInstance(project)
        val workspace = workspaceService.findWorkspaceFor(normalizedRoot) ?: return cached?.changes.orEmpty()
        val effectiveWorkspaceType = workspaceService.effectiveWorkspaceType(workspace)
        val statusScope = when (effectiveWorkspaceType) {
            WorkspaceType.SERVER -> workspace.serverPath
            WorkspaceType.LOCAL, WorkspaceType.UNKNOWN -> workspace.localRoot.toString()
        }
        val result = TfCommandRunner.getInstance(project).run(
            args = TfvcCommandBuilder.status(statusScope),
            workingDirectory = workspace.localRoot,
        )
        if (!result.isSuccessfulLike) {
            if (result.stderr.isNotBlank()) {
                TfvcNotifications.warn(
                    project,
                    "TFVC status refresh failed",
                    "Fix tf.exe access or workspace state externally, then retry.\n${result.stderr}",
                )
            }
            return cached?.changes.orEmpty()
        }

        val parsed = TfStatusParser.parse(
            output = result.stdout,
            resolveServerItem = { serverItem -> TfvcPathMapper.toLocalPath(workspace, serverItem) },
            onUnresolvedServerItem = { serverItem ->
                if (TfvcSettingsState.getInstance(project).verboseCommandLogging) {
                    TfvcNotifications.logToConsole(
                        project,
                        "Skipping TFVC status entry outside mapped root ${workspace.localRoot}: $serverItem",
                    )
                }
            },
        )
        cache[normalizedRoot] = CachedStatus(System.currentTimeMillis(), parsed)
        return parsed
    }

    fun hasPendingChange(file: Path): Boolean {
        val workspace = TfvcWorkspaceService.getInstance(project).findWorkspaceFor(file) ?: return false
        val normalized = file.normalize()
        return statusForRoot(workspace.localRoot).any { it.localPath.normalize() == normalized && !it.isCandidate }
    }

    fun rootsForPaths(paths: Collection<Path>): Set<Path> =
        paths.mapNotNull { TfvcWorkspaceService.getInstance(project).findWorkspaceFor(it)?.localRoot }
            .toSet()

    fun invalidate(root: Path? = null) {
        if (root == null) {
            cache.clear()
            return
        }
        val normalizedRoot = root.normalize()
        cache.keys.removeIf { key -> key == normalizedRoot || key.startsWith(normalizedRoot) || normalizedRoot.startsWith(key) }
    }

    fun requestRefresh(root: Path) {
        invalidate(root)
        refreshQueue.queue(
            object : Update(root.normalize().toString()) {
                override fun run() {
                    val virtualFile = LocalFileSystem.getInstance().findFileByNioFile(root.normalize()) ?: return
                    VcsDirtyScopeManager.getInstance(project).dirDirtyRecursively(virtualFile)
                }
            },
        )
    }

    companion object {
        fun getInstance(project: Project): TfvcStatusCache = project.service()
    }
}
