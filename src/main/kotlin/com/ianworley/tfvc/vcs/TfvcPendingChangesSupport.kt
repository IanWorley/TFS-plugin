package com.ianworley.tfvc.vcs

import com.ianworley.tfvc.tf.TfCommandRunner
import com.ianworley.tfvc.tf.TfvcCommandBuilder
import com.ianworley.tfvc.tf.TfvcNotifications
import com.ianworley.tfvc.tf.TfvcWorkspaceService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import java.nio.file.Path

class TfvcPendingChangesSupport(
    private val project: Project,
) {
    fun add(files: Collection<VirtualFile>): List<VcsException> =
        runOperation(
            commandName = "add",
            items = files,
            filePathProvider = { VcsUtil.getFilePath(it.toNioPath(), it.isDirectory) },
            commandBuilder = { scopedFiles -> TfvcCommandBuilder.add(scopedFiles.map(VirtualFile::toNioPath)) },
        )

    fun delete(paths: Collection<FilePath>): List<VcsException> =
        runOperation(
            commandName = "delete",
            items = paths,
            filePathProvider = { it },
            commandBuilder = TfvcCommandBuilder::delete,
        )

    fun checkin(
        paths: Collection<FilePath>,
        comment: String?,
        feedback: MutableSet<in String>? = null,
    ): List<VcsException> =
        runOperation(
            commandName = "checkin",
            items = paths,
            filePathProvider = { it },
            commandBuilder = { scopedPaths -> TfvcCommandBuilder.checkin(scopedPaths, comment) },
            onFailure = { root, message ->
                TfvcNotifications.logToConsole(project, "TFVC check-in failed for $root: $message")
            },
            onSuccess = { root, _ ->
                feedback?.add("TFVC check-in succeeded for $root")
            },
        )

    private fun <T> runOperation(
        commandName: String,
        items: Collection<T>,
        filePathProvider: (T) -> FilePath,
        commandBuilder: (Collection<T>) -> List<String>,
        onFailure: (Path, String) -> Unit = { _, _ -> },
        onSuccess: (Path, Collection<T>) -> Unit = { _, _ -> },
    ): List<VcsException> {
        if (items.isEmpty()) {
            return emptyList()
        }

        val errors = mutableListOf<VcsException>()
        val groupedItems = groupByRoot(items, filePathProvider, commandName, errors)

        groupedItems.forEach { (root, scopedItems) ->
            val result = TfCommandRunner.getInstance(project).run(
                args = commandBuilder(scopedItems),
                workingDirectory = root,
            )
            if (!result.isSuccessfulLike) {
                val message = result.stderr.ifBlank { "tf $commandName failed." }
                errors += VcsException("TFVC $commandName failed for $root: $message")
                onFailure(root, message)
                return@forEach
            }

            refreshTouchedPaths(scopedItems.map(filePathProvider), root)
            TfvcStatusCache.getInstance(project).requestRefresh(root)
            onSuccess(root, scopedItems)
        }

        return errors
    }

    private fun <T> groupByRoot(
        items: Collection<T>,
        filePathProvider: (T) -> FilePath,
        commandName: String,
        errors: MutableList<VcsException>,
    ): Map<Path, List<T>> {
        val workspaceService = TfvcWorkspaceService.getInstance(project)
        val groupedItems = linkedMapOf<Path, MutableList<T>>()

        items.forEach { item ->
            val filePath = filePathProvider(item)
            val workspace = workspaceService.findWorkspaceFor(filePath.ioFile.toPath())
            if (workspace == null) {
                errors += VcsException(
                    "TFVC $commandName failed for ${filePath.path}: item is not mapped to a TFVC workspace.",
                )
                return@forEach
            }

            groupedItems.getOrPut(workspace.localRoot) { mutableListOf() }.add(item)
        }

        return groupedItems
    }

    private fun refreshTouchedPaths(paths: Collection<FilePath>, root: Path) {
        val refreshTargets = linkedSetOf<Path>()
        paths.forEach { path ->
            val nioPath = path.ioFile.toPath()
            when {
                nioPath.toFile().exists() -> refreshTargets.add(nioPath)
                path.parentPath != null -> refreshTargets.add(path.parentPath!!.ioFile.toPath())
                else -> refreshTargets.add(root)
            }
        }
        refreshTargets.add(root)
        LocalFileSystem.getInstance().refreshNioFiles(refreshTargets)
    }
}
