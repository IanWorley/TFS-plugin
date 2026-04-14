package com.ianworley.tfvc.vcs

import com.ianworley.tfvc.tf.TfvcWorkspaceService
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

object TfvcActionSupport {
    fun selectedFiles(event: AnActionEvent): List<VirtualFile> {
        val directSelection = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.toList().orEmpty()
        if (directSelection.isNotEmpty()) {
            return directSelection.distinct()
        }

        val editorFile = event.getData(CommonDataKeys.VIRTUAL_FILE)
        if (editorFile != null) {
            return listOf(editorFile)
        }

        return event.getData(VcsDataKeys.CHANGES)
            ?.mapNotNull { change ->
                change.afterRevision?.file?.path
                    ?: change.beforeRevision?.file?.path
            }
            ?.mapNotNull(LocalFileSystem.getInstance()::findFileByPath)
            ?.distinct()
            .orEmpty()
    }

    fun tfvcFiles(project: Project, files: Collection<VirtualFile>): List<VirtualFile> {
        val workspaceService = TfvcWorkspaceService.getInstance(project)
        return files.filter { workspaceService.findWorkspaceFor(it.toNioPath()) != null }
    }

    fun firstRelevantRoot(project: Project, files: Collection<VirtualFile>): Path? {
        val workspaceService = TfvcWorkspaceService.getInstance(project)
        return files.firstNotNullOfOrNull { workspaceService.findWorkspaceFor(it.toNioPath())?.localRoot }
            ?: mappedTfvcRoots(project).firstOrNull()
    }

    fun mappedTfvcRoots(project: Project): List<Path> =
        ProjectLevelVcsManager.getInstance(project).allVcsRoots
            .filter { it.vcs?.name == TfvcVcs.NAME }
            .map { it.path.toNioPath() }

    fun runBackground(project: Project, title: String, work: (ProgressIndicator) -> Unit) {
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, title, false) {
                override fun run(indicator: ProgressIndicator) {
                    work(indicator)
                }
            },
        )
    }
}
