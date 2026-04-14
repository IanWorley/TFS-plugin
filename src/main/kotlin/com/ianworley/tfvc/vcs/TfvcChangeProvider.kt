package com.ianworley.tfvc.vcs

import com.ianworley.tfvc.tf.PendingChange
import com.ianworley.tfvc.tf.PendingChangeType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManagerGate
import com.intellij.openapi.vcs.changes.ChangelistBuilder
import com.intellij.openapi.vcs.changes.ChangeProvider
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.openapi.vcs.changes.VcsDirtyScope
import com.intellij.vcsUtil.VcsUtil

class TfvcChangeProvider(
    private val project: Project,
) : ChangeProvider {
    override fun getChanges(
        dirtyScope: VcsDirtyScope,
        builder: ChangelistBuilder,
        progress: ProgressIndicator,
        addGate: ChangeListManagerGate,
    ) {
        val roots = ProjectLevelVcsManager.getInstance(project).allVcsRoots
            .filter { it.vcs?.name == TfvcVcs.NAME }
            .map { it.path.toNioPath() }

        val statusCache = TfvcStatusCache.getInstance(project)
        roots.forEach { root ->
            statusCache.statusForRoot(root).forEach { pending ->
                val filePath: FilePath = VcsUtil.getFilePath(pending.localPath.toFile())
                if (pending.isCandidate) {
                    builder.processUnversionedFile(filePath)
                } else {
                    builder.processChange(buildChange(filePath, pending), TfvcVcs.KEY)
                }
            }
        }
    }

    override fun isModifiedDocumentTrackingRequired(): Boolean = false

    override fun doCleanup(files: MutableList<com.intellij.openapi.vfs.VirtualFile>) = Unit

    private fun buildChange(filePath: FilePath, pending: PendingChange): Change {
        val beforeRevision: ContentRevision? = when (pending.changeType) {
            PendingChangeType.ADD -> null
            else -> TfvcPlaceholderContentRevision(
                filePath,
                "TFVC base content is not available in v1 for ${pending.changeType.name.lowercase()} changes.",
            )
        }
        val afterRevision: ContentRevision? = when (pending.changeType) {
            PendingChangeType.DELETE -> null
            else -> CurrentContentRevision.create(filePath)
        }

        val status = when (pending.changeType) {
            PendingChangeType.ADD -> FileStatus.ADDED
            PendingChangeType.DELETE -> FileStatus.DELETED
            PendingChangeType.RENAME -> FileStatus.MODIFIED
            PendingChangeType.EDIT -> FileStatus.MODIFIED
            PendingChangeType.BRANCH -> FileStatus.ADDED
            PendingChangeType.UNDELETE -> FileStatus.ADDED
            PendingChangeType.OTHER -> FileStatus.MODIFIED
        }

        return Change(beforeRevision, afterRevision, status)
    }
}
