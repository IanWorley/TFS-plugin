package com.ianworley.tfvc.vcs

import com.ianworley.tfvc.tf.TfCommandRunner
import com.ianworley.tfvc.tf.TfvcCommandBuilder
import com.ianworley.tfvc.tf.TfvcNotifications
import com.ianworley.tfvc.tf.TfvcWorkspaceService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.EditFileProvider
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

class TfvcEditFileProvider(
    private val project: Project,
) : EditFileProvider {
    override fun getRequestText(): String = "TFVC checkout is required before editing these files."

    override fun editFiles(files: Array<out VirtualFile>) {
        checkoutFiles(files.toList())
    }

    fun checkoutFiles(files: Collection<VirtualFile>) {
        val workspaceService = TfvcWorkspaceService.getInstance(project)
        val byRoot = files
            .filter { !it.isDirectory }
            .groupBy { workspaceService.findWorkspaceFor(it.toNioPath())?.localRoot }
            .filterKeys { it != null }

        byRoot.forEach { (root, scopedFiles) ->
            val resolvedRoot = root ?: return@forEach
            val result = TfCommandRunner.getInstance(project).run(
                args = TfvcCommandBuilder.checkout(scopedFiles.map(VirtualFile::toNioPath)),
                workingDirectory = resolvedRoot,
            )
            if (!result.isSuccessfulLike) {
                val message = result.stderr.ifBlank { "tf checkout failed." }
                TfvcNotifications.error(
                    project,
                    "TFVC checkout failed",
                    "Fix tf.exe access or workspace state externally, then retry.\n$message",
                )
                throw VcsException(message)
            }

            scopedFiles.forEach { it.refresh(false, false) }
            TfvcStatusCache.getInstance(project).requestRefresh(resolvedRoot)
        }
    }
}
