package com.ianworley.tfvc.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinEnvironment
import com.intellij.openapi.vfs.VirtualFile

class TfvcCheckinEnvironment(
    project: Project,
) : CheckinEnvironment {
    private val pendingChangesSupport = TfvcPendingChangesSupport(project)

    override fun getCheckinOperationName(): String = "Check In"

    override fun getHelpId(): String? = null

    override fun isRefreshAfterCommitNeeded(): Boolean = true

    override fun scheduleUnversionedFilesForAddition(files: List<VirtualFile>): List<VcsException> =
        pendingChangesSupport.add(files)

    override fun scheduleMissingFileForDeletion(files: List<FilePath>): List<VcsException> =
        pendingChangesSupport.delete(files)

    override fun commit(
        changes: List<Change>,
        commitMessage: String,
        commitContext: CommitContext,
        feedback: MutableSet<in String>,
    ): List<VcsException> {
        val paths = changes.asSequence()
            .mapNotNull { it.afterRevision?.file ?: it.beforeRevision?.file }
            .distinctBy { it.path to it.isDirectory }
            .toList()

        return pendingChangesSupport.checkin(paths, commitMessage, feedback)
    }
}
