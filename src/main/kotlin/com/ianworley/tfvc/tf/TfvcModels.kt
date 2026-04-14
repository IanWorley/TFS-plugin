package com.ianworley.tfvc.tf

import java.nio.file.Path

enum class WorkspaceType {
    LOCAL,
    SERVER,
    UNKNOWN,
}

enum class PendingChangeType {
    EDIT,
    ADD,
    DELETE,
    RENAME,
    BRANCH,
    UNDELETE,
    OTHER,
}

enum class TfCommandDisposition {
    SUCCESS,
    PARTIAL_SUCCESS,
    COMMAND_ERROR,
    HARD_FAILURE,
    UNKNOWN,
}

data class WorkspaceContext(
    val localRoot: Path,
    val serverPath: String,
    val workspaceName: String,
    val owner: String,
    val collectionUrl: String,
    val workspaceType: WorkspaceType,
)

data class TfCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
) {
    val disposition: TfCommandDisposition
        get() = when (exitCode) {
            0 -> TfCommandDisposition.SUCCESS
            1 -> TfCommandDisposition.PARTIAL_SUCCESS
            2 -> TfCommandDisposition.COMMAND_ERROR
            100 -> TfCommandDisposition.HARD_FAILURE
            else -> TfCommandDisposition.UNKNOWN
        }

    val isSuccessfulLike: Boolean
        get() = disposition == TfCommandDisposition.SUCCESS || disposition == TfCommandDisposition.PARTIAL_SUCCESS
}

data class PendingChange(
    val localPath: Path,
    val changeType: PendingChangeType,
    val isCandidate: Boolean,
    val user: String,
    val locked: Boolean,
)

data class ShelvesetSummary(
    val name: String,
    val owner: String,
    val comment: String,
)
