package com.ianworley.tfvc.tf

import com.ianworley.tfvc.parsing.TfWorkfoldParser
import com.ianworley.tfvc.parsing.TfWorkspacesXmlParser
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

class TfvcWorkspaceService(
    private val project: Project,
) {
    private val cache = ConcurrentHashMap<Path, WorkspaceContext?>()

    fun findWorkspaceFor(path: Path): WorkspaceContext? {
        val probe = normalizeProbe(path)
        return cache.computeIfAbsent(probe) { discoverWorkspace(it) }
    }

    fun isRoot(path: Path): Boolean {
        val workspace = findWorkspaceFor(path) ?: return false
        return workspace.localRoot == path.normalize()
    }

    fun invalidate(path: Path? = null) {
        if (path == null) {
            cache.clear()
            return
        }

        val normalized = normalizeProbe(path)
        cache.keys.removeIf { key -> key.startsWith(normalized) || normalized.startsWith(key) }
    }

    private fun discoverWorkspace(path: Path): WorkspaceContext? {
        val runner = TfCommandRunner.getInstance(project)
        val workfold = runner.run(
            args = TfvcCommandBuilder.workfold(path),
            workingDirectory = path.takeIf(Files::isDirectory) ?: path.parent,
        )
        if (!workfold.isSuccessfulLike) {
            return null
        }

        val base = TfWorkfoldParser.parse(workfold.stdout) ?: return null
        val workspaces = runner.run(
            args = TfvcCommandBuilder.workspaces(),
            workingDirectory = base.localRoot,
        )
        if (!workspaces.isSuccessfulLike) {
            return base
        }

        val enriched = TfWorkspacesXmlParser.parse(workspaces.stdout).firstOrNull {
            it.localRoot == base.localRoot &&
                (base.workspaceName.isBlank() || it.workspaceName.equals(base.workspaceName, ignoreCase = true))
        }

        return if (enriched == null) {
            base
        } else {
            base.copy(
                workspaceName = enriched.workspaceName.ifBlank { base.workspaceName },
                owner = enriched.owner.ifBlank { base.owner },
                collectionUrl = enriched.collectionUrl.ifBlank { base.collectionUrl },
                workspaceType = if (enriched.workspaceType == WorkspaceType.UNKNOWN) base.workspaceType else enriched.workspaceType,
            )
        }
    }

    private fun normalizeProbe(path: Path): Path =
        when {
            Files.isDirectory(path) -> path.normalize()
            path.parent != null -> path.parent.normalize()
            else -> path.normalize()
        }

    companion object {
        fun getInstance(project: Project): TfvcWorkspaceService = project.service()
    }
}
